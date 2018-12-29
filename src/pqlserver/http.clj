(ns pqlserver.http
  "Custom code for HTTP communication"
  (:require [clojure.java.io :as io]
            [clojure.core.async :as async]
            [pqlserver.pooler :refer [datasource]]
            [clojure.java.jdbc :as jdbc]
            [ring.util.io :refer [piped-input-stream]]))

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

(defn chan-seq!!
  "Create a lazy sequence of channel takes"
  [c]
  (lazy-seq (when-let [v (async/<!! c)]
              (cons v (chan-seq!! c)))))

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
