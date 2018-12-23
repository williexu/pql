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
       ["SELECT people.name, people.age FROM people WHERE people.name = ?" "susan"]))
