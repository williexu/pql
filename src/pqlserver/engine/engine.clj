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

(defprotocol SQLGen
  (-plan->hsql [query]))

(extend-protocol SQLGen
  Query
  (-plan->hsql [query]
    query)

  BinaryExpression
  (-plan->hsql [{:keys [column operator value] :as args}]
    [operator (-plan->hsql column) (-plan->hsql value)]))

(defn plan->hsql
  [query]
  (println "CONVER TQUERY" query)
  (-plan->hsql query))

(defn honeysql-from-query
  [query])

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
            [["=" column value]]
            (let [info (get-in query-rec [:projections (keyword column)])]
              (map->BinaryExpression {:operator :=
                                      :column info
                                      :value value}))))


(defn plan->honeysql
  "Convert a plan to a honeysql datastructure"
  [query-rec plan]
  (println "QUERY REC" query-rec)
  (println "PLAN IS" (-plan->hsql plan))
  (let [base-query (:selection query-rec)
        selection (->> (vals (:projections query-rec))
                       (mapv :field))
        sqlmap {:select selection
                :from base-query
                :where (-plan->hsql plan)}]

    (println "SQLMAP" sqlmap)
    sqlmap))

(defn query->sql
  [schema query]
  (println "THIS QUERY" query)
  (let [{:keys [entity remaining-query]} (parse-query-context query)
        query-rec (get schema entity)
        plan (user-node->plan-node query-rec remaining-query)
        honeysql (plan->honeysql query-rec plan)
        compiled-stmt (hcore/format honeysql)]
    (println "COMPILED" compiled-stmt)
    query))
