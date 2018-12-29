(ns pqlserver.engine
  "Parse AST to HoneySQL, to SQL.

   Conversion of AST to HoneySQL is managed by two recursive descent parsers,
   applied in succession. The first parser (node->plan) converts the AST to a
   'plan tree' of nodes conforming to the types specified in the defrecords
   below. The second parser (plan->hsql) converts the plan tree to HoneySQL.
   Conversion from HoneySQL to proper SQL is handled by the HoneySQL formatter
   functions. The parsers are separated in anticipation of additional supported
   database backends."
  (:require [clojure.core.match :as cm]
            [zip.visit :as zv]
            [clojure.zip :as z]
            [honeysql.core :as hc]
            [honeysql.format :as hf]))

(defmethod hf/fn-handler "~" [_ field pattern]
  (str (hf/to-sql field) " ~ " (hf/to-sql pattern)))

(defmethod hf/fn-handler "~*" [_ field pattern]
  (str (hf/to-sql field) " ~* " (hf/to-sql pattern)))

(def binary-op?
  #{:= :< :<= :> :>= (keyword "~") (keyword "~*")})

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
(defrecord GroupByExpression [subquery groupings])
(defrecord FieldExpression [projection])
(defrecord FnExpression [function args])

(defn node->plan
  "Codifies a parse tree from parser.clj to a query plan. This parser is
   database-agnostic."
  [schema node]
  (cm/match [node]
            [[(op :guard binary-op?) column value]]
            (map->BinaryExpression {:operator op
                                    :column (node->plan schema column)
                                    :value value})

            [[:orderparam field direction]]
            (map->OrderExpression
              {:field field
               :direction direction})

            [[:field field]]
            (let [context (:context (meta node))
                  qualified-field (-> schema context :projections field :field)]
              (map->FieldExpression
                {:projection qualified-field}))

            [[:function function & args]]
            (map->FnExpression
              {:function function
               :args (mapv (partial node->plan schema) args)})

            [[:order-by subquery orderings]]
            (map->OrderByExpression
              {:subquery (node->plan schema subquery)
               :orderings (mapv (partial node->plan schema) orderings)})

            [[:group-by subquery groupings]]
            (map->GroupByExpression
              {:subquery (node->plan schema subquery)
               :groupings (mapv (partial node->plan schema) groupings)})

            [[:limit subquery limit]]
            (map->LimitExpression
              {:subquery (node->plan schema subquery)
               :limit limit})

            [[:offset subquery offset]]
            (map->OffsetExpression
              {:subquery (node->plan schema subquery)
               :offset offset})

            [[:from (entity :guard keyword?) [:extract columns & expr]]]
            (let [{:keys [selection] :as query-rec} (get schema entity)]
              (map->FromExpression
                {:projections (mapv (partial node->plan schema) columns)
                 :subquery selection
                 :where (some->> (first expr)
                                 (node->plan schema))}))

            [[:from (entity :guard keyword?) expr]]
            (let [{:keys [selection projections]} (get schema entity)]
              (map->FromExpression
                {:projections (mapv :field (vals projections))
                 :subquery selection
                 :where (when (not-empty expr)
                          (node->plan schema expr))}))

            [[:and & exprs]]
            (map->AndExpression
              {:clauses (map (partial node->plan schema) exprs)})

            [[:or & exprs]]
            (map->OrExpression
              {:clauses (map (partial node->plan schema) exprs)})

            [[:null? column value]]
            (let [column (node->plan schema column)]
              (map->NullExpression {:column column :null? value}))

            [[:in column subquery]]
            (map->InExpression
              {:column (node->plan schema column)
               :subquery (node->plan schema subquery)})

            [[:not expr]]
            (map->NotExpression
              {:clause (node->plan schema expr)})))

(defprotocol SQLGen
  "Protocol for converting plan nodes to HoneySQL. This will need adaptation to
   generalize beyond Postgres."
  (-plan->hsql [node]))

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

  GroupByExpression
  (-plan->hsql [{:keys [subquery groupings]}]
    (-> (-plan->hsql subquery)
        (assoc :group-by (map -plan->hsql groupings))))

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

  FnExpression
  (-plan->hsql [{:keys [function args]}]
    (let [args (if (empty? args)
                 [:*]
                 (map -plan->hsql args))]
      (apply hc/call function args)))

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

(def update-meta
  "Annotates the metadata of each `from` node with `context` equal to the
   relevant entity. This is necessary because clauses like limit, offset,
   and order-by require knowledge of fields they are operating against
   for validation, but such context is unavailable in a top-down parse of the
   AST. Annotating the AST prior to parsing eliminates the need to keep track
   of this context while parsing."
  (zv/visitor :pre [n s]
              (when (and (vector? n) (= :from (first n)))
                {:node (vary-meta n assoc :context (second n))
                 :state (second n)})))

(def fill-meta
  (zv/visitor
    :post [n s]
    (let [ctx (or (:context (meta n)) s)]
      (when (vector? n)
        {:node (vary-meta n assoc :context ctx)
         :state ctx}))))

(defn query->sql
  [schema query]
  (let [annotated-query (-> (z/vector-zip query)
                            (zv/visit nil [update-meta fill-meta])
                            :node)]
    (->> annotated-query
         (node->plan schema)
         plan->hsql
         hc/format)))
