(ns pqlserver.engine
  "Parse AST to HoneySQL, to SQL. This two-step process entails converting AST
   to a plan tree, then converting the plan to HoneySQL, from which SQL can
   easily be emitted."
  (:require [clojure.core.match :as cm]
            [honeysql.core :as hcore]
            [honeysql.format :as hfmt]))

(defmethod hfmt/fn-handler "~" [_ field pattern]
  (str (hfmt/to-sql field) " ~ " (hfmt/to-sql pattern)))

(defmethod hfmt/fn-handler "~*" [_ field pattern]
  (str (hfmt/to-sql field) " ~* " (hfmt/to-sql pattern)))

(def binary-op?
  #{:= :< :<= :> :>= (keyword "~") (keyword "~*")})

(defprotocol SQLGen
  "Translates a plan to HoneySQL"
  (-plan->hsql [node]))

;; Plan node types
(defrecord Query [projections selection])
(defrecord AndExpression [clauses])
(defrecord BinaryExpression [operator column value])
(defrecord FromExpression [projections subquery where limit offset order-by])
(defrecord InExpression [column subquery])
(defrecord NotExpression [clause])
(defrecord OrExpression [clauses])
(defrecord RegexExpression [column value])
(defrecord NullExpression [column null?])

(defn node->plan
  "Translates AST to a plan"
  [schema context node]
  (cm/match [node]
            [[(op :guard binary-op?) column value]]
            (let [field (-> schema context :projections column :field)]
              (map->BinaryExpression {:operator op
                                      :column field
                                      :value value}))

            [[:from (entity :guard keyword?) [:extract columns & expr] & paging-term]]
            (let [{:keys [selection] :as query-rec} (get schema entity)
                  projections (mapv #(-> query-rec :projections % :field) columns)
                  {:keys [limit offset order-by]} (first paging-term)]
              (map->FromExpression
                {:projections projections
                 :subquery selection
                 :where (some->> (first expr)
                                 (node->plan schema entity))
                 :limit limit
                 :order-by order-by
                 :offset offset}))

            [[:from (entity :guard keyword?) expr & paging-term]]
            (let [{:keys [selection projections]} (get schema entity)
                  {:keys [limit offset order-by]} (first paging-term)]
              (map->FromExpression
                {:projections (mapv :field (vals projections))
                 :subquery selection
                 :where (when (not-empty expr)
                          (node->plan schema entity expr))
                 :limit limit
                 :order-by order-by
                 :offset offset}))

            [[:and & exprs]]
            (map->AndExpression
              {:clauses (map (partial node->plan schema context) exprs)})

            [[:or & exprs]]
            (map->OrExpression
              {:clauses (map (partial node->plan schema context) exprs)})

            [[:null? column value]]
            (let [column (-> schema context :projections column)]
              (map->NullExpression {:column column :null? value}))

            [[:in column subquery]]
            (map->InExpression
              {:column (-> schema context :projections column :field)
               :subquery (node->plan schema context subquery)})

            [[:not expr]]
            (map->NotExpression
              {:clause (node->plan schema context expr)})))

(extend-protocol SQLGen
  Query
  (-plan->hsql [node] node)

  BinaryExpression
  (-plan->hsql [{:keys [column operator value]}]
    [operator column (cond-> value (keyword? value) name)])

  FromExpression
  (-plan->hsql [{:keys [projections subquery where limit offset order-by]}]
    (-> {:select projections
         :from [(:from subquery)]}
        (cond-> where (assoc :where (-plan->hsql where)))
        (cond-> limit (assoc :limit limit))
        (cond-> offset (assoc :offset offset))
        (cond-> order-by (assoc :order-by order-by))))

  AndExpression
  (-plan->hsql [{:keys [clauses]}]
    (->> (map -plan->hsql clauses)
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
  (-plan->hsql [{:keys [clauses]}]
    (->> (map -plan->hsql clauses)
         (concat [:or])))

  NotExpression
  (-plan->hsql [{:keys [clause]}]
    [:not (-plan->hsql clause)])

  Object
  (-plan->hsql [obj] obj))

(defn plan->hsql [plan]
  (-plan->hsql plan))

(defn query->sql
  [schema query]
  (->> query
       (node->plan schema nil)
       plan->hsql
       hcore/format))
