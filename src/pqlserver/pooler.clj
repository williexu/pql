(ns pqlserver.pooler
  (:require [hikari-cp.core :as hk]
            [clojure.java.jdbc :as jdbc]))


(def datasource-options
  {:auto-commit false
   :read-only true
   :connection-timeout 30000
   :validation-timeout 5000
   :idle-timeout 600000
   :max-lifetime 1800000
   :minimum-idle 10
   :maximum-pool-size 10
   :pool-name "db-pool"
   :adapter "postgresql"
   :database-name "foo"})


(defonce datasource
  (delay (hk/make-datasource datasource-options)))
