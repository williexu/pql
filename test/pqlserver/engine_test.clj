(ns pqlserver.engine-test
  (:require [clojure.test :refer :all]
            [pqlserver.engine :refer :all]))


(def test-schema
  {:people {:fields {:name {:type :string
                                 :field :people.name}
                          :age {:type :number
                                :field :people.age}}
            :base {:from :people}}
   :pets {:fields {:name {:type :string
                               :field :pets.name}
                        :owner {:type :string
                                :field :pets.owner}}
          :base {:from :pets}}})

(deftest test-engine
  (are [input expected] (= expected (query->sql test-schema input))
       [:from :people []]
       ["SELECT people.name, people.age FROM people"]

       [:from :people [:= [:field :name] :susan]]
       ["SELECT people.name, people.age FROM people WHERE people.name = ?" "susan"]

       [:from :people [:and [:= [:field :name] :susan] [:> [:field :age] 30]]]
       ["SELECT people.name, people.age FROM people WHERE (people.name = ? AND people.age > ?)" "susan" 30]

       [:from :people [:or [:= [:field :name] :susan] [:> [:field :age] 30]]]
       ["SELECT people.name, people.age FROM people WHERE (people.name = ? OR people.age > ?)" "susan" 30]

       [:from :people [:or [:= [:field :name] :susan] [:and [:> [:field :age] 30] [:< [:field :age] 100]]]]
       ["SELECT people.name, people.age FROM people WHERE (people.name = ? OR (people.age > ? AND people.age < ?))" "susan" 30 100]

       [:from :people [:not [:= [:field :name] :susan]]]
       ["SELECT people.name, people.age FROM people WHERE NOT people.name = ?" "susan"]

       [:from :people [(keyword "~") [:field :name] :susan]]
       ["SELECT people.name, people.age FROM people WHERE people.name ~ ?" "susan"]

       [:from :people [:not [(keyword "~") [:field :name] :susan]]]
       ["SELECT people.name, people.age FROM people WHERE NOT people.name ~ ?" "susan"]

       [:from :people [:not [(keyword "~*") [:field :name] :susan]]]
       ["SELECT people.name, people.age FROM people WHERE NOT people.name ~* ?" "susan"]

       [:from :people [:extract [[:field :name]] [:not [(keyword "~*") [:field :name] :susan]]]]
       ["SELECT people.name FROM people WHERE NOT people.name ~* ?" "susan"]

       [:from :people [:extract [[:field :name] [:field :age]] [:not [(keyword "~*") [:field :name] :susan]]]]
       ["SELECT people.name, people.age FROM people WHERE NOT people.name ~* ?" "susan"]

       [:from :people [:extract [[:field :name] [:field :age]]
                       [:in [:field :name] [:from :pets [:extract [[:field :name]] [:= [:field :owner] :foobar]]]]]]
       ["SELECT people.name, people.age FROM people WHERE (people.name in (SELECT pets.name FROM pets WHERE pets.owner = ?))" "foobar"]

       [:from :people [:in [:field :name] [:from :pets [:extract [[:field :name]] [:= [:field :owner] :foobar]]]]]
       ["SELECT people.name, people.age FROM people WHERE (people.name in (SELECT pets.name FROM pets WHERE pets.owner = ?))" "foobar"]

       [:from :people [:null? [:field :name] true]]
       ["SELECT people.name, people.age FROM people WHERE people.name IS NULL"]

       [:from :people [:null? [:field :name] false]]
       ["SELECT people.name, people.age FROM people WHERE people.name IS NOT NULL"]

       [:limit [:from :people [:null? [:field :name] false]] 1]
       ["SELECT people.name, people.age FROM people WHERE people.name IS NOT NULL LIMIT ?" 1]

       [:offset [:limit [:from :people [:null? [:field :name] false]] 1] 10]
       ["SELECT people.name, people.age FROM people WHERE people.name IS NOT NULL LIMIT ? OFFSET ?" 1 10]

       [:limit [:offset [:from :people [:null? [:field :name] false]] 10] 1]
       ["SELECT people.name, people.age FROM people WHERE people.name IS NOT NULL LIMIT ? OFFSET ?" 1 10]

       [:limit [:offset [:order-by [:from :people [:null? [:field :name] false]] [[:orderparam [:field :name] [:direction :asc]]]] 10] 1]
       ["SELECT people.name, people.age FROM people WHERE people.name IS NOT NULL ORDER BY name ASC LIMIT ? OFFSET ?" 1 10]

       [:order-by [:offset [:limit [:from :people [:null? [:field :name] false]] 1] 10] [[:orderparam [:field :name] [:direction :asc]]]]
       ["SELECT people.name, people.age FROM people WHERE people.name IS NOT NULL ORDER BY name ASC LIMIT ? OFFSET ?" 1 10]

       [:order-by [:offset [:limit [:from :people [:null? [:field :name] false]] 1] 10] [[:orderparam [:field :name] [:direction :desc]]]]
       ["SELECT people.name, people.age FROM people WHERE people.name IS NOT NULL ORDER BY name DESC LIMIT ? OFFSET ?" 1 10]

       [:order-by [:offset [:limit [:from :people [:null? [:field :name] false]] 1] 10]
        [[:orderparam [:field :name] [:direction :desc]] [:orderparam [:field :age] [:direction :asc]]]]
       ["SELECT people.name, people.age FROM people WHERE people.name IS NOT NULL ORDER BY name DESC, age ASC LIMIT ? OFFSET ?"
        1 10]

        [:order-by [:offset [:limit [:from :people [:null? [:field :name] false]] 1] 10]
         [[:orderparam [:field :name] [:direction :desc]] [:orderparam [:field :age] [:direction :asc]]]]
       ["SELECT people.name, people.age FROM people WHERE people.name IS NOT NULL ORDER BY name DESC, age ASC LIMIT ? OFFSET ?"
        1 10]

       [:order-by [:offset [:limit [:from :people [:null? [:field :name] false]] 1] 10]
        [[:orderparam [:field :name] [:direction :asc]] [:orderparam [:field :age] [:direction :desc]]]]
       ["SELECT people.name, people.age FROM people WHERE people.name IS NOT NULL ORDER BY name ASC, age DESC LIMIT ? OFFSET ?"
        1 10]

       [:order-by [:offset [:limit [:from :people [:null? [:field :name] false]] 1] 10]
        [[:orderparam [:field :name] [:direction :asc]] [:orderparam [:field :age] [:direction :desc]]]]
       ["SELECT people.name, people.age FROM people WHERE people.name IS NOT NULL ORDER BY name ASC, age DESC LIMIT ? OFFSET ?"
        1 10]

       [:limit [:from :people []] 10]
       ["SELECT people.name, people.age FROM people LIMIT ?" 10]

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

       [:group-by [:from :people [:extract [[:field :name] [:function :count [:field :age] [:field :name]]]]] [[:field :name]]]
       ["SELECT people.name, count(people.age, people.name) FROM people GROUP BY people.name"]

       [:group-by [:from :people [:extract [[:field :name]] [:null? [:json-query :name :foo] false]]] [[:field :name] [:field :age]]]
       ["SELECT people.name FROM people WHERE name->>'foo' IS NOT NULL GROUP BY people.name, people.age"]

       [:group-by [:from :people [:extract [[:field :name]] [:null? [:json-query :name :foo :bar] false]]] [[:field :name] [:field :age]]]
       ["SELECT people.name FROM people WHERE name->'foo'->>'bar' IS NOT NULL GROUP BY people.name, people.age"]

       ))
