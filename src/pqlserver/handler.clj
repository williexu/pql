(ns pqlserver.handler
  (:require [cheshire.core :as json]
            [clojure.core.async :as async]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [pqlserver.engine :refer [query->sql]]
            [pqlserver.http :refer [query->chan chan-seq!! query]]
            [ring.util.io :refer [piped-input-stream]]
            [pqlserver.parser :refer [pql->ast]]
            [ring.util.response :as rr])
  (:import [java.io IOException]))

(defn json-response
  "Produce a json ring response"
  ([body]
   (json-response body 200))
  ([body code]
  (-> body
      rr/response
      (rr/content-type "application/json; charset=utf-8")
      (rr/status code))))

(defn wrapped-generate-stream
  "This function wraps a call to json/generate-stream with a try/catch. It's
   necessary due to what I believe is a bug in the clojure compiler, which is
   demonstrated here:
   https://github.com/wkalt/streaming-demo"
  [result-seq cancel-fn writer opts]
  (try
    (json/generate-stream result-seq writer opts)
    (catch IOException e
      ;; These are client-side cancellations, so we log debug
      (log/debug e "Error streaming response")
      (cancel-fn))
    (catch Exception e
      (log/error e "Error streaming response")
      (cancel-fn))))

(defn make-routes
  [pool api-spec]
  (routes
    (GET "/" [] "Hello World")
    (GET "/query/:version" [query version]
         (try
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
                 ;; piped-input-stream. Although we do not track the state of
                 ;; the future, we know that it has finished its work when
                 ;; result-seq is fully consumed, which blocks this function's
                 ;; exit.
                 _ (future (query->chan pool sql result-chan kill?))
                 result-seq (chan-seq!! result-chan)
                 pp (-> json/default-pretty-print-options
                        (assoc :indent-arrays? true)
                        json/create-pretty-printer)]
             (piped-input-stream
               #(let [w (io/make-writer % {:encoding "UTF-8"})]
                  (wrapped-generate-stream result-seq cancel-fn w {:pretty pp}))))
           (catch Exception e
             (rr/bad-request (.getMessage e)))))
    (GET "/plan/:version" [query version explain]
         (try
           (let [version-kwd (keyword version)
                 sql (->> query
                          pql->ast
                          (query->sql api-spec version-kwd))]
             (-> {:query (first sql) :parameters (rest sql)}
                 (json/generate-string {:pretty true})
                 json-response))
           (catch Exception e
             (rr/bad-request (.getMessage e)))))
    (GET "/describe" []
         (-> api-spec
             (json/generate-string {:pretty true})
             json-response))
    (route/not-found "Not Found")))
