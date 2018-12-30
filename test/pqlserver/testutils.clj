(ns pqlserver.testutils
  (:require [pqlserver.pooler :as pooler]))

(def test-db {:database-name "foo"})

(defn test-pool []
  (pooler/make-datasource test-db))
