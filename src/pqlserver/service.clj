(ns pqlserver.service
  (:require [clojure.tools.cli :as cli]
            [clojure.tools.logging :as log]
            [yaml.core :as yaml]
            [clojure.tools.nrepl.server :as nrepl]
            [pqlserver.json :as pql-json]
            [pqlserver.pooler :as pooler]
            [pqlserver.schema :as schema]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [pqlserver.handler :as handler]))

(def cli-options
  [["-c" "--config CONFIG" "Configuration file"
    :default nil
    :parse-fn yaml/from-file]
   ["-s" "--spec SPEC" "API specification"
    :default nil
    :parse-fn #(-> % slurp clojure.edn/read-string)]
   ["-g" "--generate" "Generate an API specification to stdout"]])

(defn validate-opts
  [opts]
  (when-not (:config opts)
    (println "Specify a config file with -c or --config")
    (System/exit 1))
  (when-not (or (:generate opts) (:spec opts))
    (println "Indicate a specification file with --spec, or generate one with --generate")
    (System/exit 1))
  (when (and (:generate opts) (:spec opts))
    (println "--generate and --spec are mutually exclusive")
    (System/exit 1))

  ;; config is valid
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
        {:keys [port] :as jetty-opts} (-> opts :config :webserver)
        spec (:spec opts)
        routes (handler/make-routes pool spec)]
    (if (:generate opts)
      (do (schema/print-schema pool)
          (System/exit 0))
      (do (pql-json/add-common-json-encoders!)
          (log/infof "Serving on port %d" port)
          (-> routes
              (wrap-defaults api-defaults)
              (run-jetty jetty-opts))))))
