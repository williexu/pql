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
(defrecord FromExpression [projections subquery where])
(defrecord InExpression [column subquery])
(defrecord NotExpression [clause])
(defrecord OrExpression [clauses])
(defrecord RegexExpression [column value])
(defrecord NullExpression [column null?])
(defrecord LimitExpression [subquery limit])
(defrecord OffsetExpression [subquery limit])
(defrecord OrderByExpression [subquery orderings])
(defrecord OrderExpression [field direction])
(defrecord FieldExpression [projection])
(defrecord FunctionExpression [function args])

(defn node->plan
  "Translates AST to a plan"
  [schema context node]
  (cm/match [node]
            [[(op :guard binary-op?) column value]]
            (map->BinaryExpression {:operator op
                                    :column (node->plan schema context column)
                                    :value value})

            [[:orderparam field direction]]
            (map->OrderExpression
              {:field field
               :direction direction})

            [[:field field]]
            (let [qualified-field (-> schema context :projections field :field)]
              (map->FieldExpression
                {:projection qualified-field}))

            [[:function function & args]]
            (map->FunctionExpression
              {:function function
               :args args})

            [[:order-by subquery orderings]]
            (map->OrderByExpression
              {:subquery (node->plan schema context subquery)
               :orderings (mapv (partial node->plan schema context) orderings)})

            [[:limit subquery limit]]
            (map->LimitExpression
              {:subquery (node->plan schema context subquery)
               :limit limit})

            [[:offset subquery offset]]
            (map->OffsetExpression
              {:subquery (node->plan schema context subquery)
               :offset offset})

            [[:from (entity :guard keyword?) [:extract columns & expr]]]
            (let [{:keys [selection] :as query-rec} (get schema entity)]
              (map->FromExpression
                {:projections (mapv (partial node->plan schema entity) columns)
                 :subquery selection
                 :where (some->> (first expr)
                                 (node->plan schema entity))}))

            [[:from (entity :guard keyword?) expr]]
            (let [{:keys [selection projections]} (get schema entity)]
              (map->FromExpression
                {:projections (mapv :field (vals projections))
                 :subquery selection
                 :where (when (not-empty expr)
                          (node->plan schema entity expr))}))

            [[:and & exprs]]
            (map->AndExpression
              {:clauses (map (partial node->plan schema context) exprs)})

            [[:or & exprs]]
            (map->OrExpression
              {:clauses (map (partial node->plan schema context) exprs)})

            [[:null? column value]]
            (let [column (node->plan schema context column)]
              (map->NullExpression {:column column :null? value}))

            [[:in column subquery]]
            (map->InExpression
              {:column (node->plan schema context column)
               :subquery (node->plan schema context subquery)})

            [[:not expr]]
            (map->NotExpression
              {:clause (node->plan schema context expr)})))

(extend-protocol SQLGen
  Query
  (-plan->hsql [node] node)

  LimitExpression
  (-plan->hsql [{:keys [subquery limit]}]
    (-> (-plan->hsql subquery)
        (assoc :limit limit)))

  OffsetExpression
  (-plan->hsql [{:keys [subquery offset]}]
    (-> (-plan->hsql subquery)
        (assoc :offset offset)))

  OrderByExpression
  (-plan->hsql [{:keys [subquery orderings]}]
    (-> (-plan->hsql subquery)
        (assoc :order-by (map -plan->hsql orderings))))

  BinaryExpression
  (-plan->hsql [{:keys [column operator value]}]
    [operator (-plan->hsql column) (cond-> value (keyword? value) name)])

  FromExpression
  (-plan->hsql [{:keys [projections subquery where]}]
    (-> {:select (mapv -plan->hsql projections)
         :from [(:from subquery)]}
        (cond-> where (assoc :where (-plan->hsql where)))))

  AndExpression
  (-plan->hsql [{:keys [clauses]}]
    (->> (map -plan->hsql clauses)
         (concat [:and])))

  InExpression
  (-plan->hsql [{:keys [column subquery]}]
    [:in (-plan->hsql column)
     (-plan->hsql subquery)])

  FieldExpression
  (-plan->hsql [{:keys [projection]}]
    projection)

  FunctionExpression
  (-plan->hsql [{:keys [function args]}]
    [(apply hcore/call function args)])

  NullExpression
  (-plan->hsql [{:keys [column null?]}]
    (let [lhs (-plan->hsql column)]
      (if null?
        [:is lhs nil]
        [:is-not lhs nil])))

  OrExpression
  (-plan->hsql [{:keys [clauses]}]
    (->> (map -plan->hsql clauses)
         (concat [:or])))

  OrderExpression
  (-plan->hsql [{:keys [field direction]}]
    [(second field) (second direction)])

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
