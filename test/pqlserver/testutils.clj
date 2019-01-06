(ns pqlserver.testutils
  (:require [pqlserver.pooler :as pooler]))

(def test-db {:database-name "foo"})

(defn test-pool []
  (pooler/datasource test-db))
