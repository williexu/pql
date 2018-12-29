(ns pqlserver.parser
  (:import com.fasterxml.jackson.core.JsonParseException)
  (:require [instaparse.core :as insta]
            [clojure.string :as str]
            [clojure.core.match :as cm]
            [cheshire.core :as json]))

(defn paging-clause?
  [v]
  (= :pagingclause (first v)))

(defn update-cond
  [m pred ks f & args]
  (if pred
    (apply update-in m ks f args)
    m))

(defn update-when
  [m ks f & args]
  (let [val (get-in m ks ::not-found)]
    (apply update-cond m (not= val ::not-found) ks f args)))

(defn slurp-right
  [& vs]
  (vec (concat (first vs) (rest vs))))

(defn transform-from
  [entity & args]
  (let [paging-groups (group-by paging-clause? args)
        paging-args (sort (get paging-groups true))
        nonpaging-args (get paging-groups false)
        other-clauses (get paging-groups false)
        stripped-from (slurp-right
                        [:from (keyword entity)]
                        (apply slurp-right other-clauses))]
    ;; Pull the paging expressions up around the from expression
    (loop [query stripped-from
           args paging-args]
      (if-let [[_ [clause arg]] (first args)]
        (recur [clause query arg] (rest args))
        query))))

(defn transform-subquery
  ([entity]
   [:subquery (keyword entity)])
  ([entity arg2]
   [:subquery (keyword entity) arg2]))

(defn transform-extract
  [& args]
  [:extract (vec args)])

(defn transform-expr-or
  ([data] data)
  ([data & args] (vec (concat [:or data] args))))

(defn transform-expr-and
  ([data] data)
  ([data & args] (vec (concat [:and data] args))))

(defn transform-expr-not
  ([data] data)
  ([_ data] [:not data]))

(defn transform-function
  [entity args]
  (vec (concat [:function (keyword entity)] args)))

(defn transform-condexpression
  [a b c]
  (case b
    "!=" [:not [:= a c]]
    "!~" [:not [(keyword "~") a c]]
    "!~*" [:not [(keyword "~*") a c]]
    [(keyword b) a c]))

(defn transform-condexpnull
  [entity type]
  [:null? entity
   (case (first type)
     :condisnull true
     :condisnotnull false)])

(defn transform-groupedlist
  [& args]
  (vec args))

(defn transform-groupedliterallist
  [& args]
  [:array args])

(defn transform-sqstring
  [s]
  (str/replace s #"\\'" "'"))

(defn transform-dqstring
  [s]
  (json/parse-string (str "\"" s "\"")))

(defn transform-boolean
  [bool]
  (case (first bool)
    :true true
    :false false))

(defn transform-integer
  ([int]
   (Integer. int))
  ([neg int]
   (- (Integer. int))))

(defn transform-real
  [& args]
  (Double. (apply str args)))

(defn transform-exp
  ([int]
   (str "E" int))
  ([mod int]
   (str "E" mod int)))

(defn transform-groupby
  [& args]
  (conj [:group-by] (vec args)))

(defn transform-limit
  [arg]
  [:limit arg])

(defn transform-offset
  [arg]
  [:offset arg])

(defn transform-array
  "strip the brackets from an array and cast to a vec"
  [& args]
  (-> args rest butlast vec))

(defn transform-orderby
  [& args]
  [:order-by
   (vec (for [arg args]
          (if (= 2 (count arg))
            (conj arg [:direction :asc])
            (mapv #(cond->> % (and (vector? %) (= :direction (first %))) (mapv keyword)) arg))))])

(defn transform-jsonprojection
  [& args]
  (vec (concat [:json-query] (mapv keyword args))))

(defn transform-field
  [& args]
  [:field (keyword (str/join "." args))])

(def transform-specification
  {:extract            transform-extract
   :field              transform-field
   :jsonprojection     transform-jsonprojection
   :from               transform-from
   :subquery           transform-subquery
   :expr-or            transform-expr-or
   :expr-and           transform-expr-and
   :expr-not           transform-expr-not
   :function           transform-function
   :condexpression     transform-condexpression
   :condexpnull        transform-condexpnull
   :groupedarglist     transform-groupedlist
   :groupedfieldlist   transform-groupedlist
   :groupedregexplist  transform-groupedlist
   :groupedliterallist transform-groupedliterallist
   :sqstring           transform-sqstring
   :dqstring           transform-dqstring
   :boolean            transform-boolean
   :integer            transform-integer
   :array              transform-array
   :real               transform-real
   :exp                transform-exp
   :groupby            transform-groupby
   :limit              transform-limit
   :offset             transform-offset
   :orderby            transform-orderby})

(defn transform
  [tree]
  (insta/transform transform-specification tree))

(def parse
  (insta/parser
    (clojure.java.io/resource "pql-grammar.ebnf")))

(defn pql->ast
  [pql]
  (-> pql parse transform first))
