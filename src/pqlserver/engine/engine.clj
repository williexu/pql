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
  (str (hfmt/to-sql field) " ~ " (hfmt/to-sql pattern)))

(defmethod hfmt/fn-handler "~*" [_ field pattern]
  (str (hfmt/to-sql field) " ~* " (hfmt/to-sql pattern)))

(defrecord Query [projections selection])
(defrecord AndExpression [clauses])
(defrecord BinaryExpression [operator column value])
(defrecord ExtractExpression [columns subquery])
(defrecord FromExpression [projections subquery where])
(defrecord InExpression [column subquery])
(defrecord NotExpression [clause])
(defrecord OrExpression [clauses])
(defrecord RegexExpression [column value])
(defrecord NullExpression [column null?])

(def binary-op?
  #{:= (keyword "~") (keyword "~*") :< :<= :> :>=})

(defn stringify-key [k]
  (if (keyword? k) (name k) k))

(defprotocol SQLGen
  (-plan->hsql [node]))

(extend-protocol SQLGen
  Query
  (-plan->hsql [node] node)

  BinaryExpression
  (-plan->hsql [{:keys [column operator value]}]
    [operator column (stringify-key value)])

  FromExpression
  (-plan->hsql [{:keys [projections subquery where]}]
    {:select projections
     :from [(:from subquery)]
     :where (-plan->hsql where)})

  ExtractExpression
  (-plan->hsql [{:keys [columns subquery]}]
    {:select columns
     :from [(-plan->hsql subquery)]})

  AndExpression
  (-plan->hsql [expr]
    (->> (:clauses expr)
         (map -plan->hsql)
         (concat [:and])))

  InExpression
  (-plan->hsql [{:keys [column subquery]}]
    [:in column
     (-plan->hsql subquery)])

  NullExpression
  (-plan->hsql [{:keys [column null?]}]
    (let [lhs (-plan->hsql (:field column))]
      (if null?
        [:is lhs nil]
        [:is-not lhs nil])))

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

(defn plan->hsql [plan] (-plan->hsql plan))

(defn user-node->plan-node
  [schema context node]
  (cm/match [node]
            [[(op :guard binary-op?) column value]]
            (let [column-info (get-in schema [context :projections column])]
              (map->BinaryExpression {:operator op
                                      :column (:field column-info)
                                      :value value}))

            [[:from (entity :guard keyword?) [:extract columns & expr]]]
            (let [{:keys [selection] :as query-rec} (get schema entity)
                  projections (->> columns
                                   (map #(get-in query-rec [:projections % :field])))]
              (map->FromExpression
                {:projections (vec projections)
                 :subquery selection
                 :where (some->> (first expr)
                                 (user-node->plan-node schema entity))}))

            [[:from (entity :guard keyword?) expr]]
            (let [query-rec (get schema entity)
                  base-query (:selection query-rec)
                  projections (->> (vals (:projections query-rec))
                                   (mapv :field))]
              (map->FromExpression
                {:projections projections
                 :subquery base-query
                 :where (user-node->plan-node schema entity expr)}))

            [[:extract columns expr]]
            (map->ExtractExpression
              {:columns columns
               :subquery (user-node->plan-node schema context expr)})

            [[:and & exprs]]
            (map->AndExpression
              {:clauses (map (partial user-node->plan-node schema context) exprs)})

            [[:or & exprs]]
            (map->OrExpression
              {:clauses (map (partial user-node->plan-node schema context) exprs)})

            [[:null? column value]]
            (let [column (get-in schema [context :projections column])]
              (map->NullExpression {:column column :null? value}))

            [[:in column subquery]]
            (let [column (-> schema
                             (get-in [context :projections column])
                             :field)]
              (map->InExpression
                {:column column
                 :subquery (user-node->plan-node
                             schema context subquery)}))

            [[:not expr]]
            (map->NotExpression
              {:clause (user-node->plan-node schema context expr)})))

(defn query->sql
  [schema query]
  (->> query
       (user-node->plan-node schema nil)
       plan->hsql
       hcore/format))
