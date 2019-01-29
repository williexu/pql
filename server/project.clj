(defproject pqlserver "0.0.4-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :jvm-opts ["-Xmx128m"]
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.nrepl "0.2.13"]
                 [org.clojure/tools.cli "0.4.1"]
                 [org.clojure/core.match "0.3.0-alpha5"]
                 [io.forward/yaml "1.0.9"]
                 [instaparse "1.4.9"]
                 [clj-http "3.9.1"]
                 [cheshire "5.8.1"]
                 [compojure "1.6.1"]
                 [honeysql "0.9.4"]
                 [fast-zip "0.7.0"]
                 [zip-visit "1.1.0"]
                 [hikari-cp "2.6.0"]
                 [ring-logger "1.0.1"]
                 [puppetlabs/trapperkeeper "1.5.6"]
                 [org.clojure/java.jdbc "0.7.8"]
                 [org.clojure/core.async "0.4.490"]
                 [org.postgresql/postgresql "42.2.2"]
                 [org.clojure/tools.logging "0.4.1"]
                 [ring/ring-core "1.7.1"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [ring/ring-defaults "0.3.2"]
                 [metrics-clojure "2.10.0"]
                 [metrics-clojure-ring "2.10.0"]
                 [metrics-clojure-jvm "2.10.0"]]
  :plugins [[lein-ring "0.12.4"]]
  :main pqlserver.service
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.2"]]}})
