(ns pqlserver.utils)

(defn mapvals [m f]
  (into {} (for [[k v] m] [k (f v)])))
