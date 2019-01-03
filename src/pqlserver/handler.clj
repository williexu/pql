(ns pqlserver.handler
  (:require [cheshire.core :as json]
            [clojure.core.async :as async]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [pqlserver.engine :refer [query->sql]]
            [pqlserver.http :refer [streamed-response query->chan chan-seq!! query]]
            [pqlserver.parser :refer [pql->ast]]
            [ring.util.response :as rr]))

(defn json-response
  "Produce a json ring response"
  ([body]
   (json-response body 200))
  ([body code]
  (-> body
      rr/response
      (rr/content-type "application/json; charset=utf-8")
      (rr/status code))))

(defn make-routes
  [pool api-spec]
  (routes
    (GET "/" [] "Hello World")
    (GET "/query/:version" [query version]
         (let [version-kwd (keyword version)
               sql (->> query
                        pql->ast
                        (query->sql api-spec version-kwd))
               result-chan (async/chan)
               kill? (async/chan)
               cancel-fn #(async/>!! kill? ::cancel)
               ;; Execute the query in a future, writing records to
               ;; result-chan. In the main thread, lazily pull records off
               ;; the channel, format them to json, and write to a
               ;; piped-input-stream (via streamed-response). Although we do
               ;; not track the state of the future, we know that it has
               ;; finished its work when result-seq is fully consumed, which
               ;; blocks this function's exit.
               _ (future (query->chan pool sql result-chan kill?))
               result-seq (chan-seq!! result-chan)]
           (streamed-response buf
                              cancel-fn
                              (-> result-seq
                                  (json/generate-stream buf {:pretty true})
                                  json-response))))
    (GET "/plan/:version" [query version explain]
         (let [version-kwd (keyword version)
               sql (->> query
                        pql->ast
                        (query->sql api-spec version-kwd))]
           (-> {:query (first sql) :parameters (rest sql)}
               (json/generate-string {:pretty true})
               json-response)))
    (GET "/describe" []
         (-> api-spec
             (json/generate-string {:pretty true})
             json-response))
    (route/not-found "Not Found")))
