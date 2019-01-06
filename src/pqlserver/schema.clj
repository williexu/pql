(ns pqlserver.schema
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]))

;; Mapping from postgres types to json types
(def type-translations
  {"text" :string
   "integer" :number
   "jsonb" :object
   "array" :array
   "varchar" :string
   "boolean" :boolean
   "timestamp" :timestamp
   "timestamp with time zone" :timestamp
   "timestamp without time zone" :timestamp
   "double precision" :number})

(defn form-spec
  [m {:keys [table_name column_name data_type]}]
  (let [field (keyword (format "%s.%s" table_name column_name))
        table (keyword table_name)
        column (keyword column_name)
        typ (get type-translations (str/lower-case data_type))]
    (-> m
        (update-in [table :fields column] merge {:type typ :field field})
        (assoc-in [table :base] {:from table}))))

(defn get-schema [db]
  (->> (jdbc/query db "select table_name, column_name, data_type from
                       information_schema.columns where table_schema = 'public'")
       (reduce form-spec {})
       (assoc {} :v1)))
