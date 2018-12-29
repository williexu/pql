(ns pqlserver.http
  "Custom code for HTTP communication"
  (:require [clojure.java.io :as io]
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

