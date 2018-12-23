(ns pqlserver.handler
  (:require [compojure.core :refer :all]
            [clojure.tools.nrepl.server :refer [start-server]]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]))

(defonce nrepl-server (start-server :port 8002))

(defroutes app-routes
  (GET "/" [] "Hello World")
  (route/not-found "Not Found"))

(def app
  (wrap-defaults app-routes site-defaults))
