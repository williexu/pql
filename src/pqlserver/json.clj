(ns pqlserver.json
  "Custom json encoding extensions."
  (:require [cheshire.core :as json]
            [clj-time.coerce :as coerce]
            [cheshire.generate :refer [add-encoder encode-map encode-seq]])
  (:import [org.postgresql.util PGobject]
           [org.postgresql.jdbc PgArray]
           [com.fasterxml.jackson.core JsonGenerator]))

(defrecord RawJsonString [data])

(def ^String to-string coerce/to-string)

(defn add-common-json-encoders!*
  []
  (add-encoder
    org.postgresql.jdbc.PgArray
    (fn [^PgArray data ^JsonGenerator jsonGenerator]
      (let [array (-> data .getArray vec)]
        (encode-seq array jsonGenerator))))
  (add-encoder
    org.postgresql.util.PGobject
    (fn [^PGobject data ^JsonGenerator jsonGenerator]
      (if (.getPrettyPrinter jsonGenerator)
        (let [obj (json/parse-string (.getValue data))
              encode-fn (condp instance? obj
                          clojure.lang.IPersistentMap encode-map
                          clojure.lang.ISeq encode-seq)]
          (encode-fn obj jsonGenerator))
        (.writeRawValue jsonGenerator (.getValue data)))))
  (add-encoder
    org.joda.time.DateTime
    (fn [data ^JsonGenerator jsonGenerator]
      (.writeString jsonGenerator (to-string data))))
  (add-encoder
    RawJsonString
    (fn [data ^JsonGenerator jsonGenerator]
      (.writeRawValue jsonGenerator ^String (:data data)))))

;; Memoize this to avoid unnecessary calls to add-encoder
(def add-common-json-encoders! (memoize add-common-json-encoders!*))
