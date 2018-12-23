(ns pqlserver.engine.engine
  "Parse PQL AST to HoneySQL"
  (:require [clojure.core.match :as cm]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [honeysql.core :as hcore]
            [honeysql.format :as hfmt]
            [pqlserver.utils :as utils]
            [schema.core :as s]))

(defmethod hfmt/fn-handler "~" [_ field pattern]
  (str (hfmt/to-sql field) " ~ "
       (hfmt/to-sql pattern)))

(defmethod hfmt/fn-handler "~*" [_ field pattern]
  (str (hfmt/to-sql field) " ~* "
       (hfmt/to-sql pattern)))

(defrecord Query [projections selection])
(defrecord BinaryExpression [operator column value])
(defrecord FromExpression [projections subquery where])
(defrecord ExtractExpression [columns subquery])
(defrecord RegexExpression [column value])
(defrecord AndExpression [clauses])
(defrecord OrExpression [clauses])
(defrecord NotExpression [clause])

(defprotocol SQLGen
  (-plan->hsql [node]))

(extend-protocol SQLGen
  Query
  (-plan->hsql [node] node)

  BinaryExpression
  (-plan->hsql [{:keys [column operator value]}]
    [operator (keyword column) (-plan->hsql value)])

  FromExpression
  (-plan->hsql [{:keys [projections subquery where]}]
    {:select projections
     :from [(:from subquery)]
     :where (-plan->hsql where)})

  ExtractExpression
  (-plan->hsql [{:keys [columns subquery]}]
    {:select (mapv keyword columns)
     :from [(-plan->hsql subquery)]})

  AndExpression
  (-plan->hsql [expr]
    (->> (:clauses expr)
         (map -plan->hsql)
         (concat [:and])))

  OrExpression
  (-plan->hsql [expr]
    (->> (:clauses expr)
         (map -plan->hsql)
         (concat [:or])))

  NotExpression
  (-plan->hsql [expr]
    [:not (-plan->hsql (:clause expr))])

  Object
  (-plan->hsql [obj] obj))

(defn plan->hsql
  [plan]
  (-plan->hsql plan))

(defn user-node->plan-node
  [schema context node]
  (cm/match [node]
            [[(op :guard #{"=" "~" "~*" "<" "<=" ">" ">="}) column value]]
            (let [column-info (get-in schema [context :projections (keyword column)])]
              (map->BinaryExpression {:operator (keyword op)
                                      :column (:field column-info)
                                      :value value}))


            [["from" (entity :guard string?) ["extract" columns & expr]]]
            (let [query-rec (get schema (keyword entity))
                  projections (->> columns
                                   (map keyword)
                                   (map #(get-in query-rec [:projections % :field])))
                  base-query (:selection query-rec)]
              (map->FromExpression
                {:projections projections
                 :subquery base-query
                 :where (some->> (first expr)
                                 (user-node->plan-node schema (keyword entity)))}))

            [["from" (entity :guard string?) expr]]
            (let [query-rec (get schema (keyword entity))
                  base-query (:selection query-rec)
                  projections (->> (vals (:projections query-rec))
                                   (mapv :field))]
              (map->FromExpression
                {:projections projections
                 :subquery base-query
                 :where (user-node->plan-node
                          schema (keyword entity) expr)}))

            [["extract" columns expr]]
            (map->ExtractExpression
              {:columns columns
               :subquery (user-node->plan-node schema context expr)})

            [["and" & exprs]]
            (map->AndExpression
              {:clauses (map (partial user-node->plan-node schema context) exprs)})

            [["or" & exprs]]
            (map->OrExpression
              {:clauses (map (partial user-node->plan-node schema context) exprs)})

            [["not" expr]]
            (map->NotExpression
              {:clause (user-node->plan-node schema context expr)})))

(defn query->sql
  [schema query]
  (->> query
       (user-node->plan-node schema nil)
       plan->hsql
       hcore/format))
