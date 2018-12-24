(ns pqlserver.handler
  (:require [compojure.core :refer :all]
            [clojure.tools.nrepl.server :refer [start-server]]
            [compojure.route :as route]
            [pqlserver.parser :refer [pql->ast]]
            [pqlserver.engine :refer [query->sql]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]))

(defonce nrepl-server (start-server :port 8002))


(def test-schema
  {:people {:projections {:name {:type :string
                                 :field :people.name}
                          :age {:type :number
                                :field :people.age}}
            :selection {:from :people}}
   :pets {:projections {:name {:type :string
                               :field :pets.name}
                        :owner {:type :string
                                :field :pets.owner}}
          :selection {:from :pets}}})


(defroutes app-routes
  (GET "/" [] "Hello World")
  (GET "/query" [query]
       (println "QUERY" query)
       (->> query
            pql->ast
            (query->sql test-schema)
            first))
  (route/not-found "Not Found"))

(def app
  (wrap-defaults app-routes api-defaults))

(->> "people { name is not null order by name limit 1 offset 10}"
   pql->ast
   (query->sql test-schema)
    )


(def paging-clause? #{:limit :offset :order_by})

(let [args [[:= :name :foo]  [:limit 10]  [:offset :10]]]
  (into {} (filter #(paging-clause? (first %)) args))
  )
