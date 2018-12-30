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
   "double precision" :number})

(defn column-reducer
  [m {:keys [table_name column_name data_type]}]
  (let [field (keyword (format "%s.%s" table_name column_name))
        table (keyword table_name)
        column (keyword column_name)
        typ (get type-translations (str/lower-case data_type))]
    (-> m
        (update-in [table :fields column] merge {:type typ :field field})
        (assoc-in [table :base] {:from table}))))

(defn print-schema
  [pool]
  (jdbc/with-db-connection [conn {:datasource pool}]
    (let [columns (jdbc/query conn "select table_name, column_name, data_type from
                                  information_schema.columns where table_schema = 'public'")]
      (->> columns
           (reduce column-reducer {})
           clojure.pprint/pprint))))
