(ns pqlserver.handler-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [clojure.core.async :as async]
            [pqlserver.handler :refer :all]))

(deftest test-app
  (testing "main route"
    (let [response (app (mock/request :get "/"))]
      (is (= (:status response) 200))
      (is (= (:body response) "Hello World"))))

  (testing "not-found route"
    (let [response (app (mock/request :get "/invalid"))]
      (is (= (:status response) 404)))))

(deftest test-query->chan
  (let [future-live-ms 100]
    (testing "Future closed when all results consumed"
      (let [result-chan (async/chan)
            sql "select * from people limit 10"
            result-fut (future (query->chan sql result-chan))
            result-seq (chan-seq!! result-chan)
            results (take 10 result-seq)]
        (is (= 10 (count results)))

        ;; If we can't deref this in 100ms, the future is still open.
        (is (not= ::timeout (deref result-fut future-live-ms ::timeout)))))))
