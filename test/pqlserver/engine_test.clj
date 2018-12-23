(ns pqlserver.engine-test
  (:require [clojure.test :refer :all]
            [pqlserver.engine.engine :refer :all]))

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
  (are [input expected] (= (query->sql test-schema input) expected)
       ["from" "people" ["=" "name" "susan"]]
       ["SELECT people.name, people.age FROM people WHERE people.name = ?" "susan"]

       ["from" "people" ["and" ["=" "name" "susan"] [">" "age" 30]]]
       ["SELECT people.name, people.age FROM people WHERE (people.name = ? AND people.age > ?)" "susan" 30]

       ["from" "people" ["or" ["=" "name" "susan"] [">" "age" 30]]]
       ["SELECT people.name, people.age FROM people WHERE (people.name = ? OR people.age > ?)" "susan" 30]

       ["from" "people" ["or" ["=" "name" "susan"] ["and" [">" "age" 30] ["<" "age" 100]]]]
       ["SELECT people.name, people.age FROM people WHERE (people.name = ? OR (people.age > ? AND people.age < ?))" "susan" 30 100]

       ["from" "people" ["not" ["=" "name" "susan"]]]
       ["SELECT people.name, people.age FROM people WHERE NOT people.name = ?" "susan"]
       ))
