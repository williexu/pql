(ns pqlserver.service
  (:require [clojure.tools.cli :as cli]
            [clojure.tools.logging :as log]
            [yaml.core :as yaml]
            [clojure.tools.nrepl.server :as nrepl]
            [pqlserver.json :as pql-json]
            [pqlserver.pooler :as pooler]
            [pqlserver.schema :as schema]
            [puppetlabs.trapperkeeper.logging :refer [configure-logging!]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.logger :as ring-logger]
            [metrics.core :refer [default-registry]]
            [metrics.ring.expose :refer [expose-metrics-as-json]]
            [metrics.ring.instrument :refer [instrument-by uri-prefix]]
            [metrics.jvm.core :refer [instrument-jvm]]
            [metrics.reporters.jmx :as jmx]
            [pqlserver.handler :as handler])
  (:gen-class))

(def cli-options
  [["-c" "--config CONFIG" "Configuration file"
    :default nil
    :parse-fn yaml/from-file]
   ["-s" "--spec SPEC" "API specification"
    :default nil
    :parse-fn #(-> % slurp clojure.edn/read-string)]
   ["-g" "--generate-spec" "Generate an API specification to stdout"]])

(defn validate-opts
  [opts]
  (when-not (:config opts)
    (println "Specify a config file with -c or --config")
    (System/exit 1))
  (when-not (or (:generate-spec opts) (:spec opts))
    (println "Indicate a specification file with --spec, or generate one with --generate-spec")
    (System/exit 1))
  (when (and (:generate-spec opts) (:spec opts))
    (println "--generate-spec and --spec are mutually exclusive")
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
        {:keys [logging-config]
         :or {logging-config
              (clojure.java.io/resource "logback.xml")}} (-> opts :config :service)
        spec (:spec opts)
        routes (handler/make-routes pool spec)
        ring-logging-opts {:log-level :info
                           :request-keys [:request-method :uri :remote-addr]}
        jmx-reporter (jmx/reporter default-registry {})]
    (configure-logging! logging-config)
    (if (:generate-spec opts)
      (do (schema/print-schema pool)
          (System/exit 0))
      (do
        (instrument-jvm default-registry)
        (pql-json/add-common-json-encoders!)
        (jmx/start jmx-reporter)
        (log/infof "Serving on port %d" port)
        (-> routes
            (ring-logger/wrap-log-request-params ring-logging-opts)
            (wrap-defaults api-defaults)
            expose-metrics-as-json
            (instrument-by uri-prefix)
            (run-jetty jetty-opts))))))
