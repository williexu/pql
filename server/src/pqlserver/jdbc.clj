(ns pqlserver.jdbc
  (:require [clojure.java.jdbc :as jdbc]
            [cheshire.core :as json])
  (:import [org.postgresql.util PGobject]))

;; JDBC extensions
(extend-protocol clojure.java.jdbc/ISQLParameter

  ;; Vectors <-> arrays
  clojure.lang.IPersistentVector
  (set-parameter [v ^java.sql.PreparedStatement stmt ^long i]
    (let [conn (.getConnection stmt)
          meta (.getParameterMetaData stmt)
          type-name (.getParameterTypeName meta i)]
      (if-let [elem-type (when (= (first type-name) \_) (apply str (rest type-name)))]
        (.setObject stmt i (.createArrayOf conn elem-type (to-array v)))
        (.setObject stmt i v))))

  clojure.lang.IPersistentMap
  (set-parameter [v ^java.sql.PreparedStatement stmt ^long i]
    (let [conn (.getConnection stmt)
          meta (.getParameterMetaData stmt)
          type-name (.getParameterTypeName meta i)
          obj (doto (PGobject.)
                (.setType type-name)
                (.setValue (json/generate-string v)))]
      (.setObject stmt i obj))))

(extend-protocol clojure.java.jdbc/IResultSetReadColumn
  java.sql.Array
  (result-set-read-column [val _ _] (into [] (.getArray val)))

  PGobject
  (result-set-read-column [obj _ _]
    (let [type (.getType obj)
          value (.getValue obj)]
      (json/parse-string value true))))
