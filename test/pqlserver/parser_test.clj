(ns pqlserver.parser-test
  (:require [clojure.test :refer :all]
            [pqlserver.parser :refer :all]))


(deftest test-pql->ast
  (are [pql ast] (= ast (pql->ast pql))

       ; basic binary expression
       "people { name = 'susan' }"
       [:from :people [:= :name :susan] {}]

       ; odd spacing
       " people { name = 'susan' }"
       [:from :people [:= :name :susan] {}]

       " people { name = 'susan' } "
       [:from :people [:= :name :susan] {}]

       " people{name = 'susan' } "
       [:from :people [:= :name :susan] {}]

       " people{name='susan'} "
       [:from :people [:= :name :susan] {}]

       ; in expression
       "people { name in ['foo', 'bar', 'baz']}"
       [:from :people [:in :name [:array ["foo" "bar" "baz"]]] {}]

       ; and expression
       "people { name = 'foo' and name = 'bar'}"
       [:from :people [:and [:= :name :foo] [:= :name :bar]] {}]

       ; or expression
       "people { name = 'foo' or name = 'bar'}"
       [:from :people [:or [:= :name :foo] [:= :name :bar]] {}]

       ; combined and/or
       "people { name = 'foo' or (name = 'bar' and name = 'baz')}"
       [:from :people [:or [:= :name :foo]
                       [:and [:= :name :bar]
                        [:= :name :baz]]] {}]

       "people { name != 'susan'}"
       [:from :people [:not [:= :name :susan]] {}]

       "people { name ~ 'susan'}"
       [:from :people [(keyword "~") :name :susan] {}]

       "people { name !~ 'susan'}"
       [:from :people [:not [(keyword "~") :name :susan]] {}]

       "people { name ~* 'susan'}"
       [:from :people [(keyword "~*") :name :susan] {}]

       "people { name !~* 'susan'}"
       [:from :people [:not [(keyword "~*") :name :susan]] {}]

       "people [name] { name !~* 'susan'}"
       [:from :people [:extract [:name] [:not [(keyword "~*") :name :susan]]] {}]

       "people [name, age] { name !~* 'susan'}"
       [:from :people [:extract [:name :age] [:not [(keyword "~*") :name :susan]]] {}]

       "people [name, age] { name in pets[name] {owner = 'foobar'}}"
       [:from :people [:extract [:name :age] [:in :name [:from :pets [:extract [:name] [:= :owner :foobar]] {}]]] {}]

       "people { name in pets[name] {owner = 'foobar'}}"
       [:from :people [:in :name [:from :pets [:extract [:name] [:= :owner :foobar]] {}]] {}]

       "people { name is null }"
       [:from :people [:null? :name true] {}]

       "people { name is not null }"
       [:from :people [:null? :name false] {}]

       "people { name is not null limit 1}"
       [:from :people [:null? :name false] {:limit 1}]

       "people { name is not null limit 1 offset 10}"
       [:from :people [:null? :name false] {:limit 1 :offset 10}]

       "people { name is not null order by name limit 1 offset 10}"
       [:from :people [:null? :name false] {:limit 1 :offset 10 :order-by [[:name]]}]

       "people { name is not null order by name asc limit 1 offset 10}"
       [:from :people [:null? :name false] {:limit 1 :offset 10 :order-by [[:name :asc]]}]

       "people { name is not null order by name desc limit 1 offset 10}"
       [:from :people [:null? :name false] {:limit 1 :offset 10 :order-by [[:name :desc]]}]

       "people { name is not null order by name desc, age asc limit 1 offset 10}"
       [:from :people [:null? :name false] {:limit 1 :offset 10 :order-by [[:name :desc] [:age :asc]]}]

       "people { name is not null order by name desc, age limit 1 offset 10}"
       [:from :people [:null? :name false] {:limit 1 :offset 10 :order-by [[:name :desc] [:age]]}]

       "people { name is not null order by name, age limit 1 offset 10}"
       [:from :people [:null? :name false] {:limit 1 :offset 10 :order-by [[:name] [:age]]}]

       "people { name is not null order by name, age desc limit 1 offset 10}"
       [:from :people [:null? :name false] {:limit 1 :offset 10 :order-by [[:name] [:age :desc]]}]

       ))
