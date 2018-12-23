(ns pqlserver.parser-test
  (:require [clojure.test :refer :all]
            [pqlserver.parser :refer :all]))


(deftest test-pql->ast
  (are [pql ast] (= (pql->ast pql) ast)

       ; basic binary expression
       "people { name = 'susan' }"
       ["from" "people" ["=" "name" "susan"]]

       ; odd spacing
       " people { name = 'susan' }"
       ["from" "people" ["=" "name" "susan"]]

       " people { name = 'susan' } "
       ["from" "people" ["=" "name" "susan"]]

       " people{name = 'susan' } "
       ["from" "people" ["=" "name" "susan"]]

       " people{name='susan'} "
       ["from" "people" ["=" "name" "susan"]]


       ; in expression
       "people { name in ['foo', 'bar', 'baz']}"
       ["from" "people" ["in" "name" ["array" ["foo" "bar" "baz"]]]]

       ))
