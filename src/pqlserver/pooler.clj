(ns pqlserver.pooler
  (:require [hikari-cp.core :as hk]
            [clojure.java.jdbc :as jdbc]))

(def default-opts
  {:auto-commit false
   :read-only true
   :connection-timeout 30000
   :validation-timeout 5000
   :idle-timeout 5000
   :max-lifetime 1800000
   :maximum-pool-size 25
   :pool-name "db-pool"
   :adapter "postgresql"
   :database-name "foo"})

(defn make-datasource [opts]
  (->> opts
       (merge default-opts)
       hk/make-datasource))
