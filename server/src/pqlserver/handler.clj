(ns pqlserver.handler
  (:require [cheshire.core :as json]
            [clojure.core.async :as async]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [pqlserver.engine :refer [query->sql]]
            [pqlserver.http :refer [query->chan chan-seq!!]]
            [ring.util.io :refer [piped-input-stream]]
            [pqlserver.parser :refer [pql->ast]]
            [pqlserver.utils :refer [mapvals]]
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
    (if (:nd opts)
      (do (->> result-seq
               (map #(.write writer (str (json/generate-string %) "\n")))
               doall)
          (.flush writer))
      (json/generate-stream result-seq writer opts))
    (catch IOException e
      ;; These are client-side cancellations, so we log debug
      (log/debug e "Error streaming response")
      (cancel-fn))
    (catch Exception e
      (log/error e "Error streaming response")
      (cancel-fn))))

(defn make-routes
  [pools api-spec]
  (routes
    (GET "/" [] "Hello World")
    (GET "/:namespace/:version/query" [namespace query version newline-delimited]
         (try
           (let [version-kwd (keyword version)
                 ns-kwd (keyword namespace)
                 sql (->> query
                          pql->ast
                          (query->sql api-spec ns-kwd version-kwd))
                 result-chan (async/chan)
                 kill? (async/chan)
                 cancel-fn #(async/>!! kill? ::cancel)
                 pool (ns-kwd pools)
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
                        json/create-pretty-printer)
                 opts (cond-> {:pretty pp}
                        newline-delimited (assoc :nd true)) ]
             (piped-input-stream
               #(let [w (io/make-writer % {:encoding "UTF-8"})]
                  (wrapped-generate-stream result-seq cancel-fn w opts))))
           (catch Exception e
             (rr/bad-request (.getMessage e)))))
    (GET "/:namespace/:version/plan" [namespace query version]
         (try
           (let [version-kwd (keyword version)
                 ns-kwd (keyword namespace)
                 sql (->> query
                          pql->ast
                          (query->sql api-spec ns-kwd version-kwd))]
             (-> {:query (first sql) :parameters (rest sql)}
                 (json/generate-string {:pretty true})
                 json-response))
           (catch Exception e
             (rr/bad-request (.getMessage e)))))
    (GET "/describe-all" []
         (-> api-spec
             (json/generate-string {:pretty true})
             json-response))
    (GET "/:namespace/:version/describe" [namespace version]
         (let [version-kwd (keyword version)
               ns-kwd (keyword namespace)]
           (-> api-spec
               ns-kwd
               version-kwd
               keys
               sort
               (json/generate-string {:pretty true})
               json-response)))
    (GET "/:namespace/:version/describe/:entity" [namespace entity version]
         (let [version-kwd (keyword version)
               entity-kwd (keyword entity)
               ns-kwd (keyword namespace)]
           (-> api-spec
               ns-kwd
               version-kwd
               entity-kwd
               :fields
               (mapvals :type)
               (json/generate-string {:pretty true})
               json-response)))
    (route/not-found "Not Found")))
