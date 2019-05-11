(ns cash.plaid-client
  (:require [cemerick.uri :as uri]
            [clj-http.client :as http]
            [environ.core :refer [env]]
            [cheshire.core :as json]))

(defprotocol IPlaid
  (exchange-public-token [this public-token]
    "Exchange a public token."))

(defrecord PlaidClient [endpoint version client-id secret public-key]
  IPlaid
  (exchange-public-token
    [_this public-token]
    (http/post (str (uri/uri endpoint "/item/public_token/exchange"))
               {:request-method :put
                :body           (json/generate-string {:client_id    client-id
                                                       :secret       secret
                                                       :public_token public-token})
                :content-type   "application/json"
                :as :json})))

(defn ->sandbox
  []
  (->PlaidClient "https://sandbox.plaid.com"
                 "2018-05-22"
                 (:plaid-client-id env)
                 (:plaid-secret env)
                 (:plaid-public-key env)))