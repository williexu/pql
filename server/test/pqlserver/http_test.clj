(ns pqlserver.http-test
  (:require [clojure.core.async :as async]
            [pqlserver.http :refer :all]
            [pqlserver.testutils :refer [test-pool]]
            [clojure.test :refer :all]))

(deftest test-query->chan
  (let [future-live-ms 100
        pool (test-pool)]
    (testing "Future closed when all results consumed"
      (let [result-chan (async/chan)
            kill? (async/chan)
            sql "select * from people limit 10"
            result-fut (future (query->chan pool sql result-chan kill?))
            result-seq (chan-seq!! result-chan)
            results (take 10 result-seq)]
        (is (= 10 (count results)))

        ;; If we can't deref this in 100ms, the future is still open.
        (is (not= ::timeout (deref result-fut future-live-ms ::timeout)))))

    (testing "Future closed if cancel delivered before results"
      (let [result-chan (async/chan)
            sql "select * from people limit 10"
            kill? (async/chan)
            result-fut (future (query->chan pool sql result-chan kill?))
            result-seq (chan-seq!! result-chan)]

        ;; kill the stream before results are requested
        (async/>!! kill? ::cancel)
        (is (not= ::timeout (deref result-fut future-live-ms ::timeout)))))

    (testing "Future closed if cancel delivered after partial results"
      (let [result-chan (async/chan)
            sql "select * from people limit 10"
            kill? (async/chan)
            result-fut (future (query->chan pool sql result-chan kill?))
            result-seq (chan-seq!! result-chan)
            results (take 5 result-seq)]
        (is (= 5 (count results)))
        (async/>!! kill? ::cancel)
        (is (not= ::timeout (deref result-fut future-live-ms ::timeout)))))))
