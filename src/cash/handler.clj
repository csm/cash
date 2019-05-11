(ns cash.handler
  (:require [cash.plaid-client :as plaid]
            [clojure.tools.logging :as log]
            [compojure.api.sweet :refer :all]
            [compojure.route :as route]
            [environ.core :refer [env]]
            [ring.util.http-response :refer :all]
            [selmer.parser :refer [render-file]]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [datomic.api :as d])
  (:import [java.text SimpleDateFormat]
           [java.util Date UUID]))

(defn app-routes
  [{:keys [db-conn plaid]}]
  (api
    (GET "/link.html" []
      (content-type (ok (render-file "templates/link.html" env)) "text/html"))

    (POST "/get_access_token" request
      (let [request (update request :body #(some-> % slurp))]
        (when-let [public-token (:body request)]
          (log/debug "exchanging public token:" public-token)
          (try
            (let [{:keys [access_token item_id]} (plaid/exchange-public-token plaid public-token)]
              (log/debug "got access-token:" access_token "item-id:" item_id)
              (d/transact db-conn [{:db/id "access-token"
                                    :plaid/access-token access_token
                                    :plaid/item-id item_id}
                                   {:account/guid (UUID/randomUUID)
                                    :account/plaid "access-token"}])
              (no-content))
            (catch Exception x
              (log/warn x "exception exchanging public token")
              (internal-server-error))))))))

(defn cors
  [handler]
  (fn [request]
    (when-let [response (handler request)]
      (header response "Access-Control-Allow-Origin" "*"))))

(defn now
  []
  (.format (SimpleDateFormat. "dd/MMM/yyyy:hh:mm:ss Z") (Date.)))

(defn access
  [handler]
  (fn [request]
    (let [start (bigdec (System/nanoTime))]
      (when-let [response (handler request)]
        (let [end (bigdec (System/nanoTime))]
          (log/debug (:remote-addr request) "-" "-" (str \[ (now) \])
                     (str \" (-> request :request-method name string/upper-case)
                          " " (:uri request) " HTTP/1.1" \")
                     (:status response)
                     (or (get-header response "content-length") "-")
                     (format "%.2f" (/ (- end start) 1000000000M))
                     (or (some-> (get-header request "referer")
                                 (as-> $ (str \" $ \")))
                         "-")
                     (or (some-> (get-header request "user-agent")
                                 (as-> $ (str \" $ \")))))
          response)))))

(defn debug
  [h]
  (fn [req]
    (log/debug "request" req)
    (when-let [resp (h req)]
      (log/debug resp)
      resp)))

(defn handler
  [system]
  (routes
    (-> (app-routes system)
        cors
        access
        debug)
    (route/resources "/")
    (route/not-found "Not found.")))