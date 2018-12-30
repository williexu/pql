(ns pqlserver.handler
  (:require [compojure.core :refer :all]
            [clojure.tools.nrepl.server :refer [start-server]]
            [clojure.core.async :as async]
            [cheshire.core :as json]
            [cheshire.generate :refer [add-encoder encode-map encode-seq]]
            [clojure.tools.logging :as log]
            [compojure.route :as route]
            [pqlserver.parser :refer [pql->ast]]
            [pqlserver.engine :refer [query->sql]]
            [pqlserver.http :refer [streamed-response query->chan chan-seq!!]]
            [pqlserver.json :as pql-json]
            [ring.util.response :as rr]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]])
  (:import [java.io IOException]))

(defonce nrepl-server (start-server :port 8002))

(defn json-response
  "Produce a json ring response"
  ([body]
   (json-response body 200))
  ([body code]
  (-> body
      rr/response
      (rr/content-type "application/json; charset=utf-8")
      (rr/status code))))

(def app-routes
  (let [schema (clojure.edn/read-string (slurp (clojure.java.io/resource "schema.edn")))]
    (routes
      (GET "/" [] "Hello World")
      (GET "/query" [query]
           (let [sql (->> query
                          pql->ast
                          (query->sql schema))
                 result-chan (async/chan)
                 kill? (async/chan)
                 cancel-fn #(async/>!! kill? ::cancel)
                 ;; Execute the query in a future, writing records to
                 ;; result-chan. In the main thread, lazily pull records off
                 ;; the channel, format them to json, and write to a
                 ;; piped-input-stream (via streamed-response). Although we do
                 ;; not track the state of the future, we know that it has
                 ;; finished its work when result-seq is fully consumed.
                 _ (future (query->chan sql result-chan kill?))
                 result-seq (chan-seq!! result-chan)]
             (streamed-response buf
                                cancel-fn
                                (-> result-seq
                                    (json/generate-stream buf {:pretty true})
                                    json-response))))
      (GET "/schema" []
           (-> schema
               json-response))
      (route/not-found "Not Found"))))

(def app
  (do (pql-json/add-common-json-encoders!)
      (wrap-defaults app-routes api-defaults)))
