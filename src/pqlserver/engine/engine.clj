(ns pqlserver.engine.engine
  "This namespace is responsible for converting AST-formatted queries to
   HoneySQL, and from HoneySQL to SQL."
  (:require [clojure.string :as str]
            [clojure.core.match :as cm]
            [pqlserver.utils :as utils]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [honeysql.core :as hcore]))

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

(defprotocol SQLGen
  (-plan->hsql [query]))

(extend-protocol SQLGen
  Query
  (-plan->hsql [query]
    query)

  BinaryExpression
  (-plan->hsql [{:keys [column operator value] :as args}]
    [operator (-> column :field name -plan->hsql keyword) (-plan->hsql value)])

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
  [query-rec node]
  (cm/match [node]
            [[(op :guard #{"=" "~" "<" "<=" ">" ">="}) column value]]
            (let [info (get-in query-rec [:projections (keyword column)])]
              (map->BinaryExpression {:operator op
                                      :column info
                                      :value value}))))


(defn plan->honeysql
  "Convert a plan to a honeysql datastructure"
  [query-rec plan]
  (let [base-query (:selection query-rec)
        selection (->> (vals (:projections query-rec))
                       (mapv :field))
        sqlmap {:select selection
                :from [(:from base-query)]
                :where (plan->hsql plan)}]
    sqlmap))

(defn query->sql
  [schema query]
  (let [{:keys [entity remaining-query]} (parse-query-context query)
        query-rec (get schema entity)]
    (->> remaining-query
         (user-node->plan-node query-rec)
         (plan->honeysql query-rec)
         hcore/format)))
