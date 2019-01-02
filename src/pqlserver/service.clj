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
   ["-g" "--generate-spec FILE" "Generate an API specification to FILE"]])

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

(defn db-config->db-spec
  [cfg]
  {:dbtype "postgresql"
   :user (:username cfg)
   :password (:password cfg)
   :dbname (:database-name cfg)
   :host (:server-name cfg)})

(defn -main [& args]
  (let [opts (-> (cli/parse-opts args cli-options)
                 :options
                 validate-opts)
        db-config (-> opts :config :database)]
    (when-let [spec-file (:generate-spec opts)]
      (->> db-config
           db-config->db-spec
           schema/get-schema
           clojure.pprint/pprint
           with-out-str
           (spit spec-file))
      (System/exit 0))
    (let [pool (-> opts
                   :config
                   :database
                   (pooler/make-datasource))
          {:keys [port] :as jetty-opts} (-> opts :config :webserver)
          spec (:spec opts)
          ring-logging-opts {:log-level :info
                             :request-keys [:request-method :uri :remote-addr]}
          {:keys [logging-config]
           :or {logging-config
                (clojure.java.io/resource "logback.xml")}} (-> opts
                                                               :config
                                                               :service)]
      (nrepl/start-server :port 8002)
      ;(configure-logging! logging-config)
      (instrument-jvm default-registry)
      (pql-json/add-common-json-encoders!)
      (jmx/start (jmx/reporter default-registry {}))
      (log/infof "Serving on port %d" port)
      (-> (handler/make-routes pool spec)
          (ring-logger/wrap-log-request-params ring-logging-opts)
          (wrap-defaults api-defaults)
          expose-metrics-as-json
          (instrument-by uri-prefix)
          (run-jetty jetty-opts)))))
