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
   is irrelevant. Query cancellation is managed with the kill? channel. Putting
   a value to kill? will abort the query and exit the future cleanly.

   I'm not sure about the performance overhead of doing this on channels, but
   it seems quick enough to me at the moment."
  [db query result-chan kill?]
  (try
    (reduce (fn [_ record]
              (async/alt!!
                [kill?]
                ([v _]
                 (log/infof "Result stream received signal %s; closing" v)
                 (throw (Exception.)))
                [[result-chan record]]
                ([_ _])))
            ::init
            (jdbc/reducible-query db query))
    ;; Eat the exception; it was just for control flow.
    (catch Exception e)
    (finally
      (async/close! kill?)
      (async/close! result-chan))))

(defn chan-seq!!
  "Create a lazy sequence of channel takes"
  [c]
  (lazy-seq (when-let [v (async/<!! c)]
              (cons v (chan-seq!! c)))))

(defmacro streamed-response
  "Execute body, writing results to a piped-input-stream, which may be passed
   to a ring response."
  [writer-var cancel-fn & body]
  `(piped-input-stream
     (fn [ostream#]
       (with-open [~writer-var (io/writer ostream# :encoding "UTF-8")]
         (try
           (do ~@body)
           (catch IOException e#
             ;; IOExceptions are things like broken pipes and will mostly come
             ;; from query interrupts. No need to spam logs.
             (log/info e# "Error streaming response")
             (~cancel-fn))
           (catch Exception e#
             (log/error e# "Error streaming response")
             (~cancel-fn)))))))

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
  (let [db {:dbtype "postgresql" :dbname "foo"}
        schema (clojure.edn/read-string (slurp (clojure.java.io/resource "schema.edn")))]
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
                 _ (future (query->chan db sql result-chan kill?))
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
