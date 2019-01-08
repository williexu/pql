(ns pqlserver.fixtures
  (:require [clojure.java.jdbc :as jdbc]))


(def ^:dynamic *server-url* nil)
(def ^:dynamic *server-port* nil)


(def test-db-spec {:dbtype "postgresql"
                   :dbname "pqlserver_test"})


(defn create-test-tables
  [db-spec]
  (jdbc/db-do-commands db-spec
                       [(jdbc/create-table-ddl :people
                                              [[:name "text unique"]
                                               [:age "integer"]
                                               [:attributes "jsonb"]
                                               [:siblings "text[]"]
                                               [:street_address "text"]])
                        (jdbc/create-table-ddl :pets
                                               [[:name "text"]
                                                [:owner "text not null references people(name)"]])]))

(defn add-test-data
  [db-spec]
  (jdbc/with-db-transaction [tx db-spec]
    (let [people [{:name "Susan"
                   :age 30
                   :siblings ["Batman" "Darth Vader"]
                   :attributes {"foo" {"bar" 10}}
                   :street_address "123 Fake St."}
                  {:name "Lisa"
                   :age 8
                   :siblings ["bart"]
                   :attributes {"foo" {"bar" "baz"}}
                   :street_address "123 Fake St."}]
          pets [{:name "R2-D2"
                 :owner "Susan"}]]
      (jdbc/insert-multi! tx :people people)
      (jdbc/insert-multi! tx :pets pets))))


(defn cleanup-test-db
  [db-spec]
  (jdbc/db-do-commands db-spec
                       ["drop schema public cascade"
                        "create schema public"
                        "grant all on schema public to postgres"
                        "grant all on schema public to public"]))


(defn with-test-db [f]
  (try
    (create-test-tables test-db-spec)
    (add-test-data test-db-spec)
    (f)
    (finally
      (cleanup-test-db test-db-spec))))


;(defn with-instance [f]
;  (let [pool (test-pool)
;        spec test-db-spec
;        server (svc/start-server pool server nil nil)]
;    (println "SERVER" server)))
