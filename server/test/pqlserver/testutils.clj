(ns pqlserver.testutils
  (:require [pqlserver.pooler :as pooler]
            [pqlserver.utils :refer [mapvals]]
            [pqlserver.schema :as schema]
            [clj-time.coerce :refer [to-sql-time]]
            [clj-time.core :refer [now]]
            [pqlserver.jdbc :as _]
            [clojure.java.jdbc :as jdbc]
            [pqlserver.service :as svc]))

(def ^:dynamic *port* nil)
(def ^:dynamic *server-url* nil)

(def test-dbs
  {:test_1 {:server-name "localhost"
            :database-name "pql_test_1"
            :port-number 5432
            :username "pql_test"
            :password "pql_test"}
   :test_2 {:server-name "localhost"
            :database-name "pql_test_2"
            :port-number 5432
            :username "pql_test"
            :password "pql_test"}})

(defn uuid []
  (java.util.UUID/randomUUID))

(defn assoc-junk-columns
  "these are just to ensure things aren't broken for particular odd types"
  [m]
  (assoc m
         :varchar_col "foo"
         :varchar10_col "foo"
         :varchar16_col "foo"
         :uuid_col (uuid)
         :date_col (to-sql-time (now))
         :json_col {:foo "bar"}
         :tstz_col (to-sql-time (now))))

(defn setup-test-dbs
  [test-dbs]
  (let [db1 (svc/db-config->db-spec (:test_1 test-dbs))
        db2 (svc/db-config->db-spec (:test_2 test-dbs))
        people [{:name "abraham lincoln"
                 :age 27
                 :age_precise 2.71828
                 :attributes {:smoker false}
                 :birthday (to-sql-time "2018-01-01")
                 :siblings ["darth vader" "mr. bean"]}
                {:name "joan of arc"
                 :age 30
                 :age_precise 30.3
                 :attributes {:smoker true}
                 :birthday (to-sql-time "2018-01-01")
                 :siblings ["theresa may"]}]
        pets [{:name "jack"
               :owner "abraham lincoln"}]
        cars [{:model "toyota camry"
               :owner "joan of arc"}]]


    (jdbc/insert-multi! db1 :people (map assoc-junk-columns people))
    (jdbc/insert-multi! db1 :pets pets)

    (jdbc/insert-multi! db2 :people (map assoc-junk-columns people))
    (jdbc/insert-multi! db2 :pets pets)
    (jdbc/insert-multi! db2 :cars cars)))


(defn cleanup-test-dbs
  [test-dbs]
  (let [db1 (svc/db-config->db-spec (:test_1 test-dbs))
        db2 (svc/db-config->db-spec (:test_2 test-dbs))]
    (jdbc/db-do-commands db1
                         ["truncate table people cascade"])
    (jdbc/db-do-commands db2
                         ["truncate table people cascade"])))


(defn with-test-data [f]
  (try
    (setup-test-dbs test-dbs)
    (f)
    (finally (cleanup-test-dbs test-dbs))))

(def test-db {:database-name "foo"})

(defn open-port
  "Returns a currently open TCP port"
  []
  (with-open [s (java.net.ServerSocket. 0)]
    (.getLocalPort s)))

(defn test-pool []
  (pooler/datasource test-db))

(defn call-with-test-instance [f]
  (let [spec (atom (svc/generate-api-specs test-dbs))
        pools (svc/make-pools test-dbs)
        jetty-opts {:port (open-port) :join? false}
        server (svc/start-server pools spec nil jetty-opts)]
    (binding [*port* (:port jetty-opts)
              *server-url* (format "http://localhost:%d" (:port jetty-opts))]
      (f))
    (.stop server)))


(defmacro with-test-instance [& body]
  `(call-with-test-instance (fn [] ~@body)))
