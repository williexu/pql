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
            [pqlserver.utils :refer [mapvals]]
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

(defn start-server [pools spec logging-opts jetty-opts]
  (log/infof "Serving on port %d" (:port jetty-opts))
  (-> (handler/make-routes pools spec)
      (ring-logger/wrap-log-request-params logging-opts)
      (wrap-defaults api-defaults)
      expose-metrics-as-json
      (instrument-by uri-prefix)
      (run-jetty jetty-opts)))

(defn make-pools [namespaces]
  (reduce (fn [m v]
            (assoc m (keyword (:name v))
                   (pooler/datasource (dissoc v :name)))) {} namespaces))

(defn -main [& args]
  (let [opts (-> (cli/parse-opts args cli-options)
                 :options
                 validate-opts)
        namespaces (-> opts :config :namespaces)]
    (when-let [spec-file (:generate-spec opts)]
      (spit spec-file
            (-> #(assoc %1 (keyword (:name %2)) (db-config->db-spec %2))
                (reduce {} namespaces)
                (mapvals schema/get-schema)
                clojure.pprint/pprint
                with-out-str))
      (System/exit 0))
    (let [pools (->> opts
                     :config
                     :namespaces
                     make-pools)
          {:keys [port] :as jetty-opts} (-> opts :config :webserver)
          {:keys [nrepl-port]} (-> opts :config :development)
          spec (:spec opts)
          logging-opts {:log-level :info
                        :request-keys [:request-method :uri :remote-addr]}
          {:keys [logging-config]
           :or {logging-config
                (clojure.java.io/resource "logback.xml")}} (-> opts
                                                               :config
                                                               :service)]
      (when nrepl-port
        (nrepl/start-server :port nrepl-port))
      (configure-logging! logging-config)
      (instrument-jvm default-registry)
      (pql-json/add-common-json-encoders!)
      (jmx/start (jmx/reporter default-registry {}))
      (start-server pools spec logging-opts jetty-opts))))
