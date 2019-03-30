(ns pqlserver.service
  (:require [clojure.tools.cli :as cli]
            [clojure.tools.logging :as log]
            [clojure.tools.nrepl.server :as nrepl]
            [metrics.core :refer [default-registry]]
            [metrics.jvm.core :refer [instrument-jvm]]
            [metrics.reporters.jmx :as jmx]
            [metrics.ring.expose :refer [expose-metrics-as-json]]
            [metrics.ring.instrument :refer [instrument-by uri-prefix]]
            [overtone.at-at :as at-at]
            [pqlserver.handler :as handler]
            [pqlserver.json :as pql-json]
            [pqlserver.pooler :as pooler]
            [pqlserver.schema :as schema]
            [pqlserver.utils :refer [mapvals]]
            [puppetlabs.trapperkeeper.logging :refer [configure-logging!]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.logger :as ring-logger]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [yaml.core :as yaml])
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

(defn start-server [pools spec logging-opts jetty-opts]
  (log/infof "Serving on port %d" (:port jetty-opts))
  (-> (handler/make-routes pools spec)
      (ring-logger/wrap-log-request-params logging-opts)
      (wrap-defaults api-defaults)
      (expose-metrics-as-json "/pqlserver-metrics")
      (instrument-by uri-prefix)
      (run-jetty jetty-opts)))

(defn make-pools [namespaces]
  (-> namespaces
      (mapvals pooler/datasource)))

(defn generate-api-specs [namespaces]
  (-> namespaces
      (mapvals db-config->db-spec)
      (mapvals schema/get-schema)))

(defn schedule-api-spec-updates!
  "Updates the API spec every 30 minutes to match the deployed database schema.
   First update occurs immediately."
  [job-pool api-spec namespaces]
  (let [minutes 30
        interval (* minutes 60 1000)
        update-spec! #(do (log/info "Updating API specification")
                          (reset! api-spec (generate-api-specs namespaces))
                          (log/info "Updated API specification"))]
    (log/infof "Scheduling API specification updates every %s minutes" minutes)
    (at-at/every interval update-spec! job-pool)))

(defn -main [& args]
  (let [opts (-> (cli/parse-opts args cli-options)
                 :options
                 validate-opts)
        namespaces (-> opts :config :namespaces)
        job-pool (at-at/mk-pool)
        api-spec (atom nil)]
    (when-let [spec-file (:generate-spec opts)]
      (spit spec-file
            (-> (generate-api-specs namespaces)
                clojure.pprint/pprint
                with-out-str))
      (System/exit 0))
    (let [namespaces (->> opts :config :namespaces)
          pools (make-pools namespaces)
          jetty-opts  (-> opts :config :webserver)
          {:keys [nrepl-port]} (-> opts :config :development)
          spec (:spec opts)
          logging-opts {:log-level :info
                        :request-keys [:request-method :uri :remote-addr]}
          {:keys [logging-config]
           :or {logging-config
                (clojure.java.io/resource "logback.xml")}} (-> opts
                                                               :config
                                                               :service)]

      (if spec
        (reset! api-spec spec)
        (schedule-api-spec-updates! job-pool api-spec namespaces))

      (when nrepl-port
        (nrepl/start-server :port nrepl-port))
      (configure-logging! logging-config)
      (instrument-jvm default-registry)
      (pql-json/add-common-json-encoders!)
      (jmx/start (jmx/reporter default-registry {}))
      (start-server pools api-spec logging-opts jetty-opts))))
