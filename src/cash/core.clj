(ns cash.core
  (:require [aleph.http :as http]
            [cash.handler :refer [handler]]
            [datomic.api :as d]
            [cash.plaid-client :as plaid])
  (:import [java.util Date]))

(def schema
  [{:db/ident :account/guid
    :db/valueType :db.type/uuid
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity
    :db/index true
    :db/doc "The account GUID."}
   {:db/ident :account/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/index true
    :db/doc "The account name."}
   {:db/ident :account/type
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "The account type"}
   {:db/ident :account/institution
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "The account institution."}
   {:db/ident :account/balance
    :db/valueType :db.type/bigdec
    :db/cardinality :db.cardinality/one
    :db/doc "The current balance."}
   {:db/ident :account/collateral
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/doc "Collateral (refs to assets)."}
   {:db/ident :account/plaid
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "The Plaid access token for this account, if any."}
   [:db/add "accountType/checking" :db/ident :accountType/checking]
   [:db/add "accountType/savings" :db/ident :accountType/savings]
   [:db/add "accountType/credit-card" :db/ident :accountType/credit-card]
   [:db/add "accountType/mortgage" :db/ident :accountType/mortgage]
   [:db/add "accountType/heloc" :db/ident :accountType/heloc]
   [:db/add "accountType/ira" :db/ident :accountType/ira]
   [:db/add "accountType/-401k" :db/ident :accountType/-401k]
   {:db/ident :transaction/account
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "The account this transaction is against."}
   {:db/ident :transaction/amount
    :db/valueType :db.type/bigdec
    :db/cardinality :db.cardinality/one
    :db/doc "The amount of this transaction."}
   {:db/ident :transaction/category
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/many
    :db/index true
    :db/doc "The categories of this transaction."}
   {:db/ident :transaction/date
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one
    :db/doc "The date/time the transaction was made."}
   {:db/ident :asset/guid
    :db/valueType :db.type/uuid
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity
    :db/index true
    :db/doc "The unique GUID of an asset."}
   {:db/ident :asset/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/index true
    :db/doc "The name of the asset."}
   {:db/ident :asset/value
    :db/valueType :db.type/bigdec
    :db/cardinality :db.cardinality/one
    :db/doc "The value of this asset."}
   {:db/ident :asset/type
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "The asset type, ref to :assetType/*"}
   [:db/add "assetType/real-estate" :db/ident :assetType/real-estate]
   {:db/ident :plaid/access-token
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "A Plaid access token for an item."}
   {:db/ident :plaid/item-id
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "A Plaid item ID."}])

(defn init!
  [db-name]
  (let [db-names (set (d/get-database-names "datomic:free://localhost:4334/*"))
        exists? (some? (db-names db-name))]
    (when-not exists?
      (d/create-database (str "datomic:free://localhost:4334/" db-name)))
    (let [conn (d/connect (str "datomic:free://localhost:4334/" db-name))]
      (when-not exists?
        (d/transact conn schema))
      conn)))

(defn transact!
  [conn {:keys [account-name account-guid amount categories date]
         :or   {categories [] date (Date.)}}]
  (if-let [[account-id balance] (first
                                  (if (some? account-guid)
                                    (d/q '[:find ?a ?b :in $ ?g :where [?a :account/guid ?g]
                                           [?a :account/balance ?b]]
                                         (d/db conn) account-guid)
                                    (d/q '[:find ?a ?b :in $ ?n :where [?a :account/name ?n]
                                           [?a :account/balance ?b]]
                                         (d/db conn) account-name)))]
    (loop [retries 10]
      (let [result (try (d/transact conn [{:db/id "txn"
                                           :transaction/account  account-id
                                           :transaction/amount   amount
                                           :transaction/category categories
                                           :transaction/date     date}
                                          [:db.fn/cas account-id :account/balance balance (+ balance amount)]])
                        (catch Throwable t t))]
        (if (instance? Throwable result)
          (if (pos? retries)
            (recur (dec retries))
            (throw result))
          result)))
    (throw (IllegalArgumentException. "no such account found"))))

(defn net-worth
  [conn]
  (+ (or (ffirst (d/q '[:find (sum ?a) :in $ :where [_ :account/balance ?a]] (d/db conn)))
         0)
     (or (ffirst (d/q '[:find (sum ?a) :in $ :where [_ :asset/value ?a]] (d/db conn)))
         0)))

(defn start-server
  [plaid opts]
  (http/start-server (handler {:db-conn (init! "accounts")
                               :plaid plaid})
                     opts))

(defn start-sandbox
  [opts]
  (start-server (plaid/->sandbox) opts))
