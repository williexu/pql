(ns pqlserver.utils
  (:require [clojure.string :as str]))

(defn underscores->dashes
  "Accepts a string or a keyword, converts underscores to dashes,
   and returns the same type passed in."
  [s]
  (if (keyword? s)
    (keyword (str/replace (name s) \_ \-))
    (str/replace s \_ \-)))
