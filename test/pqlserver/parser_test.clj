(ns pqlserver.parser-test
  (:require [clojure.test :refer :all]
            [pqlserver.parser :refer :all]))


(deftest test-pql->ast
  (are [pql ast] (= ast (pql->ast pql))

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

       ; and expression
       "people { name = 'foo' and name = 'bar'}"
       ["from" "people" ["and" ["=" "name" "foo"] ["=" "name" "bar"]]]

       ; or expression
       "people { name = 'foo' or name = 'bar'}"
       ["from" "people" ["or" ["=" "name" "foo"] ["=" "name" "bar"]]]

       ; combined and/or
       "people { name = 'foo' or (name = 'bar' and name = 'baz')}"
       ["from" "people" ["or" ["=" "name" "foo"]
                         ["and" ["=" "name" "bar"] ["=" "name" "baz"]]]]

       "people { name != 'susan'}"
       ["from" "people" ["not" ["=" "name" "susan"]]]


       "people { name ~ 'susan'}"
       ["from" "people" ["~" "name" "susan"]]

       "people { name !~ 'susan'}"
       ["from" "people" ["not" ["~" "name" "susan"]]]

       "people { name ~* 'susan'}"
       ["from" "people" ["~*" "name" "susan"]]

       "people { name !~* 'susan'}"
       ["from" "people" ["not" ["~*" "name" "susan"]]]

       "people [name] { name !~* 'susan'}"
       ["from" "people" ["extract" ["name"] ["not" ["~*" "name" "susan"]]]]

       "people [name, age] { name !~* 'susan'}"
       ["from" "people" ["extract" ["name" "age"] ["not" ["~*" "name" "susan"]]]]

       "people [name, age] { name in pets[name] {owner = 'foobar'}}"
			 ["from" "people" ["extract" ["name" "age"] ["in" "name" ["from" "pets" ["extract" ["name"] ["=" "owner" "foobar"]]]]]]

       "people { name in pets[name] {owner = 'foobar'}}"
			 ["from" "people" ["in" "name" ["from" "pets" ["extract" ["name"] ["=" "owner" "foobar"]]]]]

       "people { name is null }"
       ["from" "people" ["null?" "name" true]]

       "people { name is not null }"
       ["from" "people" ["null?" "name" false]]
       ))
