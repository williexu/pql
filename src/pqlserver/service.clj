(ns pqlserver.service
  (:require [clojure.tools.cli :as cli]
            [clojure.tools.logging :as log]
            [yaml.core :as yaml]
            [clojure.tools.nrepl.server :as nrepl]
            [pqlserver.json :as pql-json]
            [pqlserver.pooler :as pooler]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [pqlserver.handler :as handler]))

(def cli-options
  [["-c" "--config CONFIG" "Configuration file"
    :default nil
    :parse-fn yaml/from-file]
   ["-s" "--spec SPEC" "API specification"
    :default nil
    :parse-fn #(-> % slurp clojure.edn/read-string)]])

(defn validate-opts
  [opts]
  (doseq [[setting error]
          [[:config "Specify a config file with -c or --config"]
           [:spec "Provide a specification file with -s or --spec"]]]
    (when-not (setting opts)
      (println error)
      (System/exit 1)))
  opts)

(defn -main [& args]
  (let [nrepl-server (nrepl/start-server :port 8002)
        opts (-> (cli/parse-opts args cli-options)
                 :options
                 validate-opts)
        pool (-> opts
                 :config
                 :database
                 (pooler/make-datasource))
        jetty-opts (-> opts :config :webserver)
        spec (:spec opts)
        routes (handler/make-routes pool spec)]

    (pql-json/add-common-json-encoders!)
    (log/infof "Serving on port %d" port)
    (-> routes
        (wrap-defaults api-defaults)
        (run-jetty jetty-opts))))
