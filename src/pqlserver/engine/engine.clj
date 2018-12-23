(ns pqlserver.engine.engine
  "This namespace is responsible for converting AST-formatted queries to
   HoneySQL, and from HoneySQL to SQL."
  (:require [clojure.string :as str]
            [clojure.core.match :as cm]
            [pqlserver.utils :as utils]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [honeysql.format :as hfmt]
            [honeysql.core :as hcore]))

; HoneySQL extensions
(defmethod hfmt/fn-handler "~" [_ field pattern]
  (str (hfmt/to-sql field) " ~ "
       (hfmt/to-sql pattern)))

(defmethod hfmt/fn-handler "~*" [_ field pattern]
  (str (hfmt/to-sql field) " ~* "
       (hfmt/to-sql pattern)))

; Node types
(defrecord Query
  [projections
   selection])

(defrecord BinaryExpression
  [operator
   column
   value])

(defrecord InExpression
  [column
   subquery])

(defrecord FromExpression
  [projections
   subquery
   where])

(defrecord ExtractExpression
  [columns
   subquery])

(defrecord RegexExpression
  [column
   value])

(defrecord AndExpression [clauses])

(defrecord OrExpression [clauses])

(defrecord NotExpression [clause])

; Engine
(defprotocol SQLGen
  (-plan->hsql [query]))

(extend-protocol SQLGen
  Query
  (-plan->hsql [query]
    query)

  BinaryExpression
  (-plan->hsql [{:keys [column operator value] :as args}]
    [operator
     (keyword column)
     (-plan->hsql value)])


  InExpression
  (-plan->hsql [{:keys [column subquery]}]
    [:in column
     {:select "foo"
      :from [(-plan->hsql subquery)]}])


  FromExpression
  (-plan->hsql [{:keys [projections subquery where]}]
    {:select projections
     :from [(:from subquery)]
     :where (-plan->hsql where)})


  ExtractExpression
  (-plan->hsql [{:keys [columns subquery]}]
    (println "COLUMNS" {:select (mapv keyword columns)
                        :from [(-plan->hsql subquery)]})
    {:select (mapv keyword columns)
     :from [(-plan->hsql subquery)]})

  AndExpression
  (-plan->hsql [expr]
    (concat [:and] (map -plan->hsql (:clauses expr))))

  OrExpression
  (-plan->hsql [expr]
    (concat [:or] (map -plan->hsql (:clauses expr))))

  NotExpression
  (-plan->hsql [expr]
    [:not (-plan->hsql (:clause expr))])

  Object
  (-plan->hsql [obj]
    obj))

(defn plan->hsql
  [query]
  (-plan->hsql query))

(defn parse-query-context
  [query]
  (cm/match query
            ["from" (entity-str :guard #(string? %)) & remaining-query]
            (let [remaining-query (cm/match remaining-query
                                            [(q :guard vector?)]
                                            q)
                  entity (keyword (utils/underscores->dashes entity-str))]
              {:entity entity
               :remaining-query remaining-query})))


(defn user-node->plan-node
  [schema context node]
  (cm/match [node]
            [[(op :guard #{"=" "~" "~*" "<" "<=" ">" ">="}) column value]]
            (let [column-info (get-in schema [context :projections (keyword column)])]
              (map->BinaryExpression {:operator (keyword op)
                                      :column (:field column-info)
                                      :value value}))

            [["from" entity where]]
            (if (string? entity)
              (let [query-rec (get schema (keyword entity))
                    base-query (:selection query-rec)
                    projections (->> (vals (:projections query-rec))
                                     (mapv :field))]
                (map->FromExpression
                  {:projections projections
                   :subquery base-query
                   :where (user-node->plan-node schema (keyword entity) where)}))
              ;; subquery
              )

            [["extract" columns expr]]
            (map->ExtractExpression
              {:columns columns
               :subquery expr})

            [["and" & exprs]]
            (map->AndExpression
              {:clauses (map (partial user-node->plan-node schema context) exprs)})

            [["or" & exprs]]
            (map->OrExpression
              {:clauses (map (partial user-node->plan-node schema context) exprs)})

            [["in" column subquery]]
            (map->InExpression
              {:column column
               :subquery (user-node->plan-node schema context subquery)})

            [["not" expr]]
            (map->NotExpression
              {:clause (user-node->plan-node schema context expr)})))

(defn query->sql
  [schema query]
  (->> query
       (user-node->plan-node schema nil)
       plan->hsql
       hcore/format))
