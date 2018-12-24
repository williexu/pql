(ns pqlserver.handler
  (:require [compojure.core :refer :all]
            [clojure.tools.nrepl.server :refer [start-server]]
            [cheshire.core :as json]
            [compojure.route :as route]
            [clojure.java.jdbc :as jdbc]
            [pqlserver.parser :refer [pql->ast]]
            [pqlserver.engine :refer [query->sql]]
            [ring.util.response :as rr]
            [ring.util.io :refer [piped-input-stream]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]])
  (:import [java.io BufferedWriter OutputStreamWriter]))

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

(defn json-response
  ([body]
   (json-response body 200))
  ([body code]
  (-> body
      rr/response
      (rr/content-type "application/json; charset=utf-8")
      (rr/status code))))

(defn generate-stream
  ([data] (generate-stream data {:pretty true}))
  ([data options]
   (piped-input-stream
     (fn [out] (json/generate-stream data
                                     (-> out
                                         (OutputStreamWriter.)
                                         (BufferedWriter.))
                                     options)))))

(def app-routes
  (let [db {:dbtype "postgresql" :dbname "foo"}
        schema test-schema]
    (routes
      (GET "/" [] "Hello World")
      (GET "/query" [query]
           (->> query
                pql->ast
                (query->sql schema)
                (jdbc/query db)
                generate-stream
                json-response))
      (GET "/schema" []
           (-> schema
               generate-stream
               json-response))
      (route/not-found "Not Found"))))

(def app
  (wrap-defaults app-routes api-defaults))
