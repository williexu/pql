(ns pqlserver.pql-integration-test
  (:require [clojure.test :refer :all]
            [pqlserver.testutils :refer [with-test-instance
                                         with-test-data
                                         *server-url*]]
            [clojure.java.shell :refer [sh with-sh-env with-sh-dir]]))


;; This runs the go tests with a server instance running

(use-fixtures :once with-test-data)

(deftest test-pql-client
  (with-test-instance
    (testing "PQL client tool tests"
      (let [wd (.getCanonicalPath (clojure.java.io/file "../pql"))]
        (with-sh-dir wd
          (with-sh-env {"PQL_TEST_SERVER_URL" *server-url*
                        "GOPATH" (System/getenv "GOPATH")}
            (let [{:keys [out exit err]} (sh "/usr/bin/go" "test" "-v" "./...")]
              (is (= 0 exit))
              (println out)
              (println err))))))))
