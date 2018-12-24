(ns pqlserver.parser
  (:import com.fasterxml.jackson.core.JsonParseException)
  (:require [instaparse.core :as insta]
            [clojure.string :as str]
            [cheshire.core :as json]))

(defn paging-clause?
  [v]
  (contains? #{"limit" "offset" "order_by"} (first v)))

(defn slurp-expr->extract
  [clauses]
  (let [paging-groups (group-by paging-clause? clauses)
        paging-clauses (get paging-groups true)
        other-clauses (get paging-groups false)]
    (if (and (= (ffirst other-clauses) "extract") (second other-clauses))
      (cons (vec (concat (first other-clauses) (rest other-clauses))) (vec paging-clauses))
      clauses)))

(defn transform-from
  [entity & args]
  (vec (concat ["from" entity] (slurp-expr->extract args))))

(defn transform-subquery
  ([entity]
   ["subquery" entity])
  ([entity arg2]
   ["subquery" entity arg2]))

(defn transform-extract
  [& args]
  ["extract" (vec args)])

(defn transform-expr-or
  ([data] data)
  ([data & args] (vec (concat ["or" data] args))))

(defn transform-expr-and
  ([data] data)
  ([data & args] (vec (concat ["and" data] args))))

(defn transform-expr-not
  ([data] data)
  ([_ data] ["not" data]))

(defn transform-function
  [entity args]
  (vec (concat ["function" entity] args)))

(defn transform-condexpression
  [a b c]
  (case b
    "!=" ["not" ["=" a c]]
    "!~" ["not" ["~" a c]]
    "!~*" ["not" ["~*" a c]]
    [b a c]))

(defn transform-condexpnull
  [entity type]
  ["null?" entity
   (case (first type)
     :condisnull true
     :condisnotnull false)])

(defn transform-groupedlist
  [& args]
  (vec args))

(defn transform-groupedliterallist
  [& args]
  ["array" args])

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
  (vec (concat ["group_by"] args)))

(defn transform-limit
  [arg]
  ["limit" arg])

(defn transform-offset
  [arg]
  ["offset" arg])

(defn transform-array
  "strip the brackets from an array and cast to a vec"
  [& args]
  (-> args rest butlast vec))

(defn transform-orderby
  [& args]
  ["order_by"
   (vec (for [arg args]
          (if (= 2 (count arg))
            (second arg)
            (vec (rest arg)))))])

(defn transform-field
  [& args]
  (str/join "." args))

(def transform-specification
  {
   :extract            transform-extract
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
   :field              transform-field
   :offset             transform-offset
   :orderby            transform-orderby})


(defn transform
  [tree]
  (insta/transform transform-specification tree))


(def parse
  (insta/parser
    (clojure.java.io/resource "pql-grammar.ebnf")))


(defn keywordize
  [ast]
  (into [] (for [k ast] (cond
                          (vector? k) (keywordize k)
                          (string? k) (keyword k)
                          :else k))))

(defn pql->ast
  [pql]
  (-> (parse pql)
      transform
      first
      keywordize))
