(ns pqlserver.handler
  (:require [compojure.core :refer :all]
            [clojure.tools.nrepl.server :refer [start-server]]
            [cheshire.core :as json]
            [cheshire.generate :refer [add-encoder encode-map encode-seq]]
            [clojure.tools.logging :as log]
            [compojure.route :as route]
            [clojure.java.jdbc :as jdbc]
            [clojure.java.io :as io]
            [clojure.core.async :as async]
            [pqlserver.parser :refer [pql->ast]]
            [pqlserver.engine :refer [query->sql]]
            [pqlserver.pooler :refer [datasource]]
            [pqlserver.json :as pql-json]
            [ring.util.response :as rr]
            [ring.util.io :refer [piped-input-stream]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]])
  (:import [java.io IOException]))

(defonce nrepl-server (start-server :port 8002))

(defn query->chan
  "Load a resultset into an async channel, blocking on puts. This is assumed
   to be called outside the main thread.

   Streaming is accomplished by performing a reduce over a jdbc reducible-query
   stream. The reduce is executed purely for side-effects, so its initial value
   is irrelevant.

   I'm not sure about the performance overhead of doing this on channels, but
   it seems quick enough to me at the moment."
  [query result-chan]
  (jdbc/with-db-connection [conn {:datasource @datasource}]
    (try
      (reduce (fn [_ record]
                (async/>!! result-chan record))
              ::init
              (jdbc/reducible-query conn query {:fetch-size 100}))
      (finally
        (async/close! result-chan)))))

(defn chan-seq!!
  "Create a lazy sequence of channel takes"
  [c]
  (lazy-seq (when-let [v (async/<!! c)]
              (cons v (chan-seq!! c)))))

(defmacro streamed-response
  "Execute body, writing results to a piped-input-stream, which may be passed
   to a ring response."
  [writer-var & body]
  `(piped-input-stream
     (fn [ostream#]
       (with-open [~writer-var (io/writer ostream# :encoding "UTF-8")]
         (try
           (do ~@body)
           (catch Exception e#
             (log/error e# "Error streaming response")))))))

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
                 ;; Execute the query in a future, writing records to
                 ;; result-chan. In the main thread, lazily pull records off
                 ;; the channel, format them to json, and write to a
                 ;; piped-input-stream (via streamed-response). Although we do
                 ;; not track the state of the future, we know it can resolve
                 ;; in two ways: if streamed-response fully consumes
                 ;; result-seq, then the associated resultset will be consumed
                 ;; and the thread will be freed. Alternatively, if
                 ;; streamed-response throws an exception, HikariCP will
                 ;; steal back the database connection after a period of
                 ;; inactivity, again allowing query->chan to exit and freeing
                 ;; the thread.
                 _ (future (query->chan sql result-chan))
                 result-seq (chan-seq!! result-chan)]
             (streamed-response buf
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
