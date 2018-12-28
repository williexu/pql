(ns pqlserver.engine-test
  (:require [clojure.test :refer :all]
            [pqlserver.engine :refer :all]))


(def test-schema
  {:people {:projections {:name {:type :string
                                 :field :people.name}
                          :age {:type :number
                                :field :people.age}}
            :selection {:from :people}}
   :pets {:projections {:name {:type :string
                               :field :pets.name}
                        :owner {:type :string
                                :field :pets.owner}}
          :selection {:from :pets}}})

(deftest test-engine
  (are [input expected] (= expected (query->sql test-schema input))
       [:from :people []]
       ["SELECT people.name, people.age FROM people"]

       [:from :people [:= :name :susan]]
       ["SELECT people.name, people.age FROM people WHERE people.name = ?" "susan"]

       [:from :people [:and [:= :name :susan] [:> :age 30]]]
       ["SELECT people.name, people.age FROM people WHERE (people.name = ? AND people.age > ?)" "susan" 30]

       [:from :people [:or [:= :name :susan] [:> :age 30]]]
       ["SELECT people.name, people.age FROM people WHERE (people.name = ? OR people.age > ?)" "susan" 30]

       [:from :people [:or [:= :name :susan] [:and [:> :age 30] [:< :age 100]]]]
       ["SELECT people.name, people.age FROM people WHERE (people.name = ? OR (people.age > ? AND people.age < ?))" "susan" 30 100]

       [:from :people [:not [:= :name :susan]]]
       ["SELECT people.name, people.age FROM people WHERE NOT people.name = ?" "susan"]

       [:from :people [(keyword "~") :name :susan]]
       ["SELECT people.name, people.age FROM people WHERE people.name ~ ?" "susan"]

       [:from :people [:not [(keyword "~") :name :susan]]]
       ["SELECT people.name, people.age FROM people WHERE NOT people.name ~ ?" "susan"]

       [:from :people [:not [(keyword "~*") :name :susan]]]
       ["SELECT people.name, people.age FROM people WHERE NOT people.name ~* ?" "susan"]

       [:from :people [:extract [:name] [:not [(keyword "~*") :name :susan]]]]
       ["SELECT people.name FROM people WHERE NOT people.name ~* ?" "susan"]

       [:from :people [:extract [:name :age] [:not [(keyword "~*") :name :susan]]]]
       ["SELECT people.name, people.age FROM people WHERE NOT people.name ~* ?" "susan"]

       [:from :people [:extract [:name :age] [:in :name [:from :pets [:extract [:name] [:= :owner :foobar]]]]]]
       ["SELECT people.name, people.age FROM people WHERE (people.name in (SELECT pets.name FROM pets WHERE pets.owner = ?))" "foobar"]

       [:from :people [:in :name [:from :pets [:extract [:name] [:= :owner :foobar]]]]]
       ["SELECT people.name, people.age FROM people WHERE (people.name in (SELECT pets.name FROM pets WHERE pets.owner = ?))" "foobar"]

       [:from :people [:null? :name true]]
       ["SELECT people.name, people.age FROM people WHERE people.name IS NULL"]

       [:from :people [:null? :name false]]
       ["SELECT people.name, people.age FROM people WHERE people.name IS NOT NULL"]

       [:limit [:from :people [:null? :name false]] 1]
       ["SELECT people.name, people.age FROM people WHERE people.name IS NOT NULL LIMIT ?" 1]

       [:offset [:limit [:from :people [:null? :name false]] 1] 10]
       ["SELECT people.name, people.age FROM people WHERE people.name IS NOT NULL LIMIT ? OFFSET ?" 1 10]

       [:limit [:offset [:from :people [:null? :name false]] 10] 1]
       ["SELECT people.name, people.age FROM people WHERE people.name IS NOT NULL LIMIT ? OFFSET ?" 1 10]

       [:limit [:offset [:order-by [:from :people [:null? :name false]] [[:name]]] 10] 1]
       ["SELECT people.name, people.age FROM people WHERE people.name IS NOT NULL ORDER BY name LIMIT ? OFFSET ?" 1 10]

       [:order-by [:offset [:limit [:from :people [:null? :name false]] 1] 10] [[:name :asc]]]
       ["SELECT people.name, people.age FROM people WHERE people.name IS NOT NULL ORDER BY name ASC LIMIT ? OFFSET ?" 1 10]

       [:order-by [:offset [:limit [:from :people [:null? :name false]] 1] 10] [[:name :desc]]]
       ["SELECT people.name, people.age FROM people WHERE people.name IS NOT NULL ORDER BY name DESC LIMIT ? OFFSET ?" 1 10]

       [:order-by [:offset [:limit [:from :people [:null? :name false]] 1] 10] [[:name :desc] [:age :asc]]]
       ["SELECT people.name, people.age FROM people WHERE people.name IS NOT NULL ORDER BY name DESC, age ASC LIMIT ? OFFSET ?"
        1 10]

        [:order-by [:offset [:limit [:from :people [:null? :name false]] 1] 10] [[:name :desc] [:age]]]
       ["SELECT people.name, people.age FROM people WHERE people.name IS NOT NULL ORDER BY name DESC, age LIMIT ? OFFSET ?"
        1 10]

       [:order-by [:offset [:limit [:from :people [:null? :name false]] 1] 10] [[:name] [:age]]]
       ["SELECT people.name, people.age FROM people WHERE people.name IS NOT NULL ORDER BY name, age LIMIT ? OFFSET ?"
        1 10]

       [:order-by [:offset [:limit [:from :people [:null? :name false]] 1] 10] [[:name] [:age :desc]]]
       ["SELECT people.name, people.age FROM people WHERE people.name IS NOT NULL ORDER BY name, age DESC LIMIT ? OFFSET ?"
        1 10]

       [:limit [:from :people []] 10]
       ["SELECT people.name, people.age FROM people LIMIT ?" 10]))
