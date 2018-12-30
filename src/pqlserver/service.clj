(ns pqlserver.service
  (:require [clojure.tools.logging :as log]
            [clojure.tools.nrepl.server :as nrepl]
            [pqlserver.json :as pql-json]
            [ring.adapter.jetty :refer [run-jetty]]
            [pqlserver.handler :refer [app]]))


(defn -main [& args]
  (let [nrepl-server (nrepl/start-server :port 8002)]
    (log/info "Updating json encoders")
    (pql-json/add-common-json-encoders!)
    (log/info "Serving on port 3000")
    (run-jetty app {:port 3000})))
