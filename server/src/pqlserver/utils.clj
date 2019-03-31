(ns pqlserver.utils
  (:import java.util.Base64))

(defn mapvals [m f]
  (into {} (for [[k v] m] [k (f v)])))

(defn b64-decode [s]
  (String. (.decode (Base64/getDecoder) s)))
