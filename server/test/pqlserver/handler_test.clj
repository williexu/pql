(ns pqlserver.handler-test
  (:require [clojure.test :refer :all]
            [cheshire.core :as json]
            [pqlserver.handler :refer :all]
            [clj-http.client :as client]
            [pqlserver.testutils :refer [with-test-instance
                                         *port*
                                         *server-url*
                                         with-test-data]]))

(use-fixtures :once with-test-data)

(deftest test-endpoint-functionality
  (with-test-instance
    (testing "/query endpoint"
      (let [params {:query-params {"query" "people[name]{attributes.smoker = 'false'}"}}
            endpoint (format "%s/test_1/v1/query" *server-url*)
            resp (client/get endpoint params)
            {:keys [name]} (first (json/parse-string (:body resp) true))]
        (is (= name "abraham lincoln"))))

    (testing "/plan endpoint"
      (let [params {:query-params {"query" "people[name]{attributes.smoker = 'false'}"}}
            endpoint (format "%s/test_1/v1/plan" *server-url*)
            resp (client/get endpoint params)
            {:keys [query]} (json/parse-string (:body resp) true)]
        (is (= "SELECT people.name FROM people WHERE attributes->>'smoker' = ?" query))))

    (testing "/describe-all"
      (let [endpoint (format "%s/describe-all" *server-url*)
            resp (client/get endpoint)
            body (json/parse-string (:body resp) true)]
        (is (= #{:test_1 :test_2} (set (keys body))))))

    (testing "/describe-api"
      (let [endpoint (format "%s/test_1/v1/describe" *server-url*)
            resp (client/get endpoint)
            body (json/parse-string (:body resp) true)]
        (is (= ["people" "pets"] body))))

    (testing "/describe-entity"
      (let [endpoint (format "%s/test_1/v1/describe/pets" *server-url*)
            resp (client/get endpoint)
            body (json/parse-string (:body resp) true)]
        (is (= {:owner "string" :name "string"} body))))))
