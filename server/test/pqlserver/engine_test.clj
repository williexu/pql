(ns pqlserver.engine-test
  (:require [clojure.test :refer :all]
            [clojure.java.jdbc :as jdbc]
            [pqlserver.fixtures :refer [with-test-db test-db-spec]]
            [pqlserver.schema :refer [get-schema]]
            [pqlserver.jdbc :as _]
            [pqlserver.engine :refer :all]))

(use-fixtures :once with-test-db)

(deftest test-engine
  (let [schema {:testing (get-schema test-db-spec)}]
    (are [input expected]
         (let [sql (query->sql schema :testing :v1 input)]
           (testing "SQL is as expected"
             (is (= expected sql)))
           (testing (format "generated SQL is valid\n%s" sql)
             (is (jdbc/query test-db-spec sql))))
         [:from :people []]
         ["SELECT people.age, people.attributes, people.name, people.siblings, people.street_address FROM people"]

         [:from :people [:= [:field :name] "susan"]]
         ["SELECT people.age, people.attributes, people.name, people.siblings, people.street_address FROM people WHERE people.name = ?" "susan"]

         [:from :people [:and [:= [:field :name] "susan"] [:> [:field :age] 30]]]
         ["SELECT people.age, people.attributes, people.name, people.siblings, people.street_address FROM people WHERE (people.name = ? AND people.age > ?)" "susan" 30]

         [:from :people [:or [:= [:field :name] "susan"] [:> [:field :age] 30]]]
         ["SELECT people.age, people.attributes, people.name, people.siblings, people.street_address FROM people WHERE (people.name = ? OR people.age > ?)" "susan" 30]

         [:from :people [:or [:= [:field :name] "susan"] [:and [:> [:field :age] 30] [:< [:field :age] 100]]]]
         ["SELECT people.age, people.attributes, people.name, people.siblings, people.street_address FROM people WHERE (people.name = ? OR (people.age > ? AND people.age < ?))" "susan" 30 100]

         [:from :people [:not [:= [:field :name] "susan"]]]
         ["SELECT people.age, people.attributes, people.name, people.siblings, people.street_address FROM people WHERE NOT people.name = ?" "susan"]

         [:from :people [(keyword "~") [:field :name] "susan"]]
         ["SELECT people.age, people.attributes, people.name, people.siblings, people.street_address FROM people WHERE people.name ~ ?" "susan"]

         [:from :people [:not [(keyword "~") [:field :name] "susan"]]]
         ["SELECT people.age, people.attributes, people.name, people.siblings, people.street_address FROM people WHERE NOT people.name ~ ?" "susan"]

         [:from :people [:not [(keyword "~*") [:field :name] "susan"]]]
         ["SELECT people.age, people.attributes, people.name, people.siblings, people.street_address FROM people WHERE NOT people.name ~* ?" "susan"]

         [:from :people [:extract [[:field :name]] [:not [(keyword "~*") [:field :name] "susan"]]]]
         ["SELECT people.name FROM people WHERE NOT people.name ~* ?" "susan"]

         [:from :people [:extract [[:field :name] [:field :age]] [:not [(keyword "~*") [:field :name] "susan"]]]]
         ["SELECT people.name, people.age FROM people WHERE NOT people.name ~* ?" "susan"]

         [:from :people [:extract [[:field :name] [:field :age]]
                         [:in [:field :name] [:from :pets [:extract [[:field :name]] [:= [:field :owner] "foobar"]]]]]]
         ["SELECT people.name, people.age FROM people WHERE (people.name in (SELECT pets.name FROM pets WHERE pets.owner = ?))" "foobar"]

         [:from :people [:in [:field :name] [:from :pets [:extract [[:field :name]] [:= [:field :owner] "foobar"]]]]]
         ["SELECT people.age, people.attributes, people.name, people.siblings, people.street_address FROM people WHERE (people.name in (SELECT pets.name FROM pets WHERE pets.owner = ?))" "foobar"]

         [:from :people [:null? [:field :name] true]]
         ["SELECT people.age, people.attributes, people.name, people.siblings, people.street_address FROM people WHERE people.name IS NULL"]

         [:from :people [:null? [:field :name] false]]
         ["SELECT people.age, people.attributes, people.name, people.siblings, people.street_address FROM people WHERE people.name IS NOT NULL"]

         [:limit [:from :people [:null? [:field :name] false]] 1]
         ["SELECT people.age, people.attributes, people.name, people.siblings, people.street_address FROM people WHERE people.name IS NOT NULL LIMIT ?" 1]

         [:offset [:limit [:from :people [:null? [:field :name] false]] 1] 10]
         ["SELECT people.age, people.attributes, people.name, people.siblings, people.street_address FROM people WHERE people.name IS NOT NULL LIMIT ? OFFSET ?" 1 10]

         [:limit [:offset [:from :people [:null? [:field :name] false]] 10] 1]
         ["SELECT people.age, people.attributes, people.name, people.siblings, people.street_address FROM people WHERE people.name IS NOT NULL LIMIT ? OFFSET ?" 1 10]

         [:limit [:offset [:order-by [:from :people [:null? [:field :name] false]] [[:orderparam [:field :name] [:direction :asc]]]] 10] 1]
         ["SELECT people.age, people.attributes, people.name, people.siblings, people.street_address FROM people WHERE people.name IS NOT NULL ORDER BY people.name ASC LIMIT ? OFFSET ?" 1 10]

         [:order-by [:offset [:limit [:from :people [:null? [:field :name] false]] 1] 10] [[:orderparam [:field :name] [:direction :asc]]]]
         ["SELECT people.age, people.attributes, people.name, people.siblings, people.street_address FROM people WHERE people.name IS NOT NULL ORDER BY people.name ASC LIMIT ? OFFSET ?" 1 10]

         [:order-by [:offset [:limit [:from :people [:null? [:field :name] false]] 1] 10] [[:orderparam [:field :name] [:direction :desc]]]]
         ["SELECT people.age, people.attributes, people.name, people.siblings, people.street_address FROM people WHERE people.name IS NOT NULL ORDER BY people.name DESC LIMIT ? OFFSET ?" 1 10]

         [:order-by [:offset [:limit [:from :people [:null? [:field :name] false]] 1] 10]
          [[:orderparam [:field :name] [:direction :desc]] [:orderparam [:field :age] [:direction :asc]]]]
         ["SELECT people.age, people.attributes, people.name, people.siblings, people.street_address FROM people WHERE people.name IS NOT NULL ORDER BY people.name DESC, people.age ASC LIMIT ? OFFSET ?"
          1 10]

         [:order-by [:offset [:limit [:from :people [:null? [:field :name] false]] 1] 10]
          [[:orderparam [:field :name] [:direction :desc]] [:orderparam [:field :age] [:direction :asc]]]]
         ["SELECT people.age, people.attributes, people.name, people.siblings, people.street_address FROM people WHERE people.name IS NOT NULL ORDER BY people.name DESC, people.age ASC LIMIT ? OFFSET ?"
          1 10]

         [:order-by [:offset [:limit [:from :people [:null? [:field :name] false]] 1] 10]
          [[:orderparam [:field :name] [:direction :asc]] [:orderparam [:field :age] [:direction :desc]]]]
         ["SELECT people.age, people.attributes, people.name, people.siblings, people.street_address FROM people WHERE people.name IS NOT NULL ORDER BY people.name ASC, people.age DESC LIMIT ? OFFSET ?"
          1 10]

         [:order-by [:offset [:limit [:from :people [:null? [:field :name] false]] 1] 10]
          [[:orderparam [:field :name] [:direction :asc]] [:orderparam [:field :age] [:direction :desc]]]]
         ["SELECT people.age, people.attributes, people.name, people.siblings, people.street_address FROM people WHERE people.name IS NOT NULL ORDER BY people.name ASC, people.age DESC LIMIT ? OFFSET ?"
          1 10]

         [:limit [:from :people []] 10]
         ["SELECT people.age, people.attributes, people.name, people.siblings, people.street_address FROM people LIMIT ?" 10]

         [:group-by [:from :people [:extract [[:field :name]]]] [[:field :name]]]
         ["SELECT people.name FROM people GROUP BY people.name"]

         [:group-by [:from :people [:extract [[:field :name]] [:null? [:field :name] false]]] [[:field :name]]]
         ["SELECT people.name FROM people WHERE people.name IS NOT NULL GROUP BY people.name"]

         [:group-by [:from :people [:extract [[:field :name]] [:null? [:field :name] false]]] [[:field :name] [:field :age]]]
         ["SELECT people.name FROM people WHERE people.name IS NOT NULL GROUP BY people.name, people.age"]

         [:group-by [:from :people [:extract [[:field :name] [:function :count]]]] [[:field :name]]]
         ["SELECT people.name, count(*) FROM people GROUP BY people.name"]

         [:group-by [:from :people [:extract [[:field :name] [:function :count [:field :age]]]]] [[:field :name]]]
         ["SELECT people.name, count(people.age) FROM people GROUP BY people.name"]

         [:group-by [:from :people [:extract [[:field :name]] [:null? [:json-query :attributes :foo] false]]] [[:field :name] [:field :age]]]
         ["SELECT people.name FROM people WHERE attributes->>'foo' IS NOT NULL GROUP BY people.name, people.age"]

         [:group-by [:from :people [:extract [[:field :name]] [:null? [:json-query :attributes :foo :bar] false]]] [[:field :name] [:field :age]]]
         ["SELECT people.name FROM people WHERE attributes->'foo'->>'bar' IS NOT NULL GROUP BY people.name, people.age"]

         [:group-by [:from :people [:extract [[:field :name]] [:= [:json-query :attributes :foo :bar] "baz"]]] [[:field :name] [:field :age]]]
         ["SELECT people.name FROM people WHERE attributes->'foo'->>'bar' = ? GROUP BY people.name, people.age" "baz"]

         [:from :people [:in [:field :name] [:array ["foo" "bar" "baz"]]]]
         ["SELECT people.age, people.attributes, people.name, people.siblings, people.street_address FROM people WHERE (people.name in (?, ?, ?))" "foo" "bar" "baz"]

         [:order-by [:group-by [:from :people [:extract [[:function :avg [:field :age]]]]] [[:field :name]]] [[:orderparam [:function :avg [:field :age]] [:direction :asc]]]]
         ["SELECT avg(people.age) FROM people GROUP BY people.name ORDER BY avg(people.age) ASC"]

         [:order-by [:group-by [:from :people [:extract [[:function :avg [:field :age]]]]] [[:field :name]]] [[:orderparam [:function :avg [:field :age]] [:direction :desc]]]]
         ["SELECT avg(people.age) FROM people GROUP BY people.name ORDER BY avg(people.age) DESC"]

         [:order-by [:group-by [:from :people [:extract [[:function :avg [:field :age]] [:function :sum [:field :age]]]]] [[:field :name]]] [[:orderparam [:function :avg [:field :age]] [:direction :desc]] [:orderparam [:function :sum [:field :age]] [:direction :asc]]]]
         ["SELECT avg(people.age), sum(people.age) FROM people GROUP BY people.name ORDER BY avg(people.age) DESC, sum(people.age) ASC"]

)))
