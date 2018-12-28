(ns pqlserver.parser-test
  (:require [clojure.test :refer :all]
            [pqlserver.parser :refer :all]))

#_(test-pql->ast)

(deftest test-pql->ast
  (are [pql ast] (= ast (pql->ast pql))

       "people {}"
       [:from :people []]

       ; basic binary expression
       "people { name = 'susan' }"
       [:from :people [:= [:field :name] :susan]]

       ; odd spacing
       " people { name = 'susan' }"
       [:from :people [:= [:field :name] :susan]]

       " people { name = 'susan' } "
       [:from :people [:= [:field :name] :susan]]

       " people{name = 'susan' } "
       [:from :people [:= [:field :name] :susan]]

       " people{name='susan'} "
       [:from :people [:= [:field :name] :susan]]

       ; in expression
       "people { name in ['foo', 'bar', 'baz']}"
       [:from :people [:in [:field :name] [:array ["foo" "bar" "baz"]]]]

       ; and expression
       "people { name = 'foo' and name = 'bar'}"
       [:from :people [:and [:= [:field :name] :foo] [:= [:field :name] :bar]]]

       ; or expression
       "people { name = 'foo' or name = 'bar'}"
       [:from :people [:or [:= [:field :name] :foo] [:= [:field :name] :bar]]]

       ; combined and/or
       "people { name = 'foo' or (name = 'bar' and name = 'baz')}"
       [:from :people [:or [:= [:field :name] :foo]
                       [:and [:= [:field :name] :bar]
                        [:= [:field :name] :baz]]]]

       "people { name != 'susan'}"
       [:from :people [:not [:= [:field :name] :susan]]]

       "people { name ~ 'susan'}"
       [:from :people [(keyword "~") [:field :name] :susan]]

       "people { name !~ 'susan'}"
       [:from :people [:not [(keyword "~") [:field :name] :susan]]]

       "people { name ~* 'susan'}"
       [:from :people [(keyword "~*") [:field :name] :susan]]

       "people { name !~* 'susan'}"
       [:from :people [:not [(keyword "~*") [:field :name] :susan]]]

       "people [name] { name !~* 'susan'}"
       [:from :people [:extract [[:field :name]] [:not [(keyword "~*") [:field :name] :susan]]]]

       "people [name, age] { name !~* 'susan'}"
       [:from :people [:extract [[:field :name] [:field :age]] [:not [(keyword "~*") [:field :name] :susan]]]]

       "people [name, age] { name in pets[name] {owner = 'foobar'}}"
       [:from :people [:extract [[:field :name] [:field :age]]
                       [:in [:field :name] [:from :pets [:extract [[:field :name]] [:= [:field :owner] :foobar]]]]]]

       "people { name in pets[name] {owner = 'foobar'}}"
       [:from :people [:in [:field :name] [:from :pets [:extract [[:field :name]] [:= [:field :owner] :foobar]]]]]

       "people { name is null }"
       [:from :people [:null? [:field :name] true]]

       "people { name is not null }"
       [:from :people [:null? [:field :name] false]]

       "people { name is not null limit 1}"
       [:limit [:from :people [:null? [:field :name] false]] 1]

       "people { name is not null limit 1 offset 10}"
       [:offset [:limit [:from :people [:null? [:field :name] false]] 1] 10]

       "people { name is not null order by name limit 1 offset 10}"
       [:order-by [:offset [:limit [:from :people [:null? [:field :name] false]] 1] 10]
        [[:orderparam [:field :name] [:direction :asc]]]]


       "people { name is not null order by name asc limit 1 offset 10}"
       [:order-by [:offset [:limit [:from :people [:null? [:field :name] false]] 1] 10]
        [[:orderparam [:field :name] [:direction :asc]]]]

       "people { name is not null order by name desc limit 1 offset 10}"
       [:order-by [:offset [:limit [:from :people [:null? [:field :name] false]] 1] 10]
        [[:orderparam [:field :name] [:direction :desc]]]]

       "people { name is not null order by name desc, age asc limit 1 offset 10}"
       [:order-by [:offset [:limit [:from :people [:null? [:field :name] false]] 1] 10]
        [[:orderparam [:field :name] [:direction :desc]] [:orderparam [:field :age] [:direction :asc]]]]

       "people { name is not null order by name desc, age limit 1 offset 10}"
       [:order-by [:offset [:limit [:from :people [:null? [:field :name] false]] 1] 10]
        [[:orderparam [:field :name] [:direction :desc]] [:orderparam [:field :age] [:direction :asc]]]]

       "people { name is not null order by name, age limit 1 offset 10}"
       [:order-by [:offset [:limit [:from :people [:null? [:field :name] false]] 1] 10]
        [[:orderparam [:field :name] [:direction :asc]] [:orderparam [:field :age] [:direction :asc]]]]

       "people { name is not null order by name, age desc limit 1 offset 10}"
       [:order-by [:offset [:limit [:from :people [:null? [:field :name] false]] 1] 10]
        [[:orderparam [:field :name] [:direction :asc]] [:orderparam [:field :age] [:direction :desc]]]]

       "people {limit 10}"
       [:limit [:from :people []] 10]
       ))
