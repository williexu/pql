# pqlserver

`pqlserver` is a generic API server. It is inspired by the PuppetDB query API,
and implements a query language very close to
[PQL](https://puppet.com/docs/puppetdb/5.1/api/query/v4/pql.html), with none of
the Puppet-specific aspects.

Use the built-in API schema generator to create an API for your database in
moments. If desired, edit the generated schema to better reflect your data
model, for instance if your database is substantially normalized, or has an
otherwise loose mapping from tables to public entities.

## Demonstration

There is a public pqlserver instance running at http://wyattalt.com:3000. The
fastest way to get a feel for the service is to install the client tool and
connect to this instance:

    $ go get github.com/wkalt/pql
    $ pql configure
    Enter a server url:
    http://wyattalt.com:3000

    $ pql query "country { name = 'Afghanistan'}"
    [ {
        "lifeexpectancy" : 45.9,
        "gnpold" : null,
        "continent" : "Asia",
        "code2" : "AF",
        "name" : "Afghanistan",
        "capital" : 1,
        "indepyear" : 1919,
        "surfacearea" : 652090.0,
        "region" : "Southern and Central Asia",
        "gnp" : 5976.00,
        "population" : 22720000,
        "localname" : "Afganistan/Afqanestan",
        "code" : "AFG",
        "governmentform" : "Islamic Emirate",
        "headofstate" : "Mohammad Omar"
    } ]

This instance is running off the "world" dataset available for download [on
pgfoundry](http://pgfoundry.org/projects/dbsamples). The API specification it
is using is generated directly from the database and is copied below:

    {:city
     {:fields
      {:id {:type :number, :field :city.id},
       :name {:type :string, :field :city.name},
       :countrycode {:type nil, :field :city.countrycode},
       :district {:type :string, :field :city.district},
       :population {:type :number, :field :city.population}},
      :base {:from :city}},
     :country
     {:fields
      {:lifeexpectancy {:type nil, :field :country.lifeexpectancy},
       :gnpold {:type nil, :field :country.gnpold},
       :continent {:type :string, :field :country.continent},
       :code2 {:type nil, :field :country.code2},
       :name {:type :string, :field :country.name},
       :capital {:type :number, :field :country.capital},
       :indepyear {:type nil, :field :country.indepyear},
       :surfacearea {:type nil, :field :country.surfacearea},
       :region {:type :string, :field :country.region},
       :gnp {:type nil, :field :country.gnp},
       :population {:type :number, :field :country.population},
       :localname {:type :string, :field :country.localname},
       :code {:type nil, :field :country.code},
       :governmentform {:type :string, :field :country.governmentform},
       :headofstate {:type :string, :field :country.headofstate}},
      :base {:from :country}},
     :countrylanguage
     {:fields
      {:countrycode {:type nil, :field :countrylanguage.countrycode},
       :language {:type :string, :field :countrylanguage.language},
       :isofficial {:type :boolean, :field :countrylanguage.isofficial},
       :percentage {:type nil, :field :countrylanguage.percentage}},
      :base {:from :countrylanguage}}}

From the schema you can see that there are three public entities: `city`,
`country`, and `countrylanguage`. Each entity has a collection of projected
`fields`, as well as a `base` selection specified in [Honey
SQL](https://github.com/jkk/honeysql). For an example of how or why the schema
would be modified, suppose I wanted to return the continent in the city entity.
I would need to add a new field under the `:city` keyword valued

    :continent {:type :string :field :country.continent}

and change the base selection for `:city` to

    :base {:from :city
           :join [:country [:= :country.code :city.countrycode]]}

## Further examples

The query language supports the following operators:

Comparison for strings and numbers
* `<=`
* `<`
* `>=`
* `>`
* `=`
* `!=`

Regular expressions
* Case-sensitive regex: `~`
* Negated case-sensitive regex: `!~`
* Case-insensitive regex: `~*`
* Negated case-insensitive regex: `!~*`

Null checks
* `is null`
* `is not null`

Additionally, supported language concepts include

* `limit`, `offset`, and ascending/decending `order by`:

    ```
    [~] $ pql query "country{ indepyear > 1900 and lifeexpectancy is not null order by lifeexpectancy limit 1}"
    [ {
      "lifeexpectancy" : 37.2,
      "gnpold" : 3922.00,
      "continent" : "Africa",
      "code2" : "ZM",
      "name" : "Zambia",
      "capital" : 3162,
      "indepyear" : 1964,
      "surfacearea" : 752618.0,
      "region" : "Eastern Africa",
      "gnp" : 3377.00,
      "population" : 9169000,
      "localname" : "Zambia",
      "code" : "ZMB",
      "governmentform" : "Republic",
      "headofstate" : "Frederick Chiluba"
    } ]
    ```

    ```
    [~] $ pql query "country{ indepyear > 1900 and lifeexpectancy is not null order by lifeexpectancy desc limit 1}"
    [ {
      "lifeexpectancy" : 80.1,
      "gnpold" : 96318.00,
      "continent" : "Asia",
      "code2" : "SG",
      "name" : "Singapore",
      "capital" : 3208,
      "indepyear" : 1965,
      "surfacearea" : 618.0,
      "region" : "Southeast Asia",
      "gnp" : 86503.00,
      "population" : 3567000,
      "localname" : "Singapore/Singapura/Xinjiapo/Singapur",
      "code" : "SGP",
      "governmentform" : "Republic",
      "headofstate" : "Sellapan Rama Nathan"
    } ]
    ```

* Column projection:

    ```
    [~] $ pql query "country[name, region] { continent = 'Antarctica'}"
    [ {
      "name" : "Antarctica",
      "region" : "Antarctica"
    }, {
      "name" : "Bouvet Island",
      "region" : "Antarctica"
    }, {
      "name" : "South Georgia and the South Sandwich Islands",
      "region" : "Antarctica"
    }, {
      "name" : "Heard Island and McDonald Islands",
      "region" : "Antarctica"
    }, {
      "name" : "French Southern territories",
      "region" : "Antarctica"
    } ]
    ```

* Joins via `in`:

    ```
    [~] $ pql query "city [name, population] {id in country[capital]{} order by population desc limit 5}"
    [ {
      "name" : "Seoul",
      "population" : 9981619
    }, {
      "name" : "Jakarta",
      "population" : 9604900
    }, {
      "name" : "Ciudad de México",
      "population" : 8591309
    }, {
      "name" : "Moscow",
      "population" : 8389200
    }, {
      "name" : "Tokyo",
      "population" : 7980230
    } ]
    ```

* `group by` and aggregate functions

    ```
    [~] $ pql query "country[avg(population), continent] { group by continent} "
    [ {
      "avg" : 15871186.956521739130,
        "continent" : "Europe"
    }, {
      "avg" : 1085755.357142857143,
        "continent" : "Oceania"
    }, {
      "avg" : 72647562.745098039216,
        "continent" : "Asia"
    }, {
      "avg" : 13053864.864864864865,
        "continent" : "North America"
    }, {
      "avg" : 13525431.034482758621,
        "continent" : "Africa"
    }, {
      "avg" : 0E-20,
        "continent" : "Antarctica"
    }, {
      "avg" : 24698571.428571428571,
        "continent" : "South America"
    } ]
    ```

    ```
    [~] $ pql query "country[count(), continent] { group by continent} "
    [ {
      "count" : 46,
        "continent" : "Europe"
    }, {
      "count" : 28,
        "continent" : "Oceania"
    }, {
      "count" : 51,
        "continent" : "Asia"
    }, {
      "count" : 37,
        "continent" : "North America"
    }, {
      "count" : 58,
        "continent" : "Africa"
    }, {
      "count" : 5,
        "continent" : "Antarctica"
    }, {
      "count" : 14,
        "continent" : "South America"
    } ]
    ```

## Supported databases

The following databases are supported:

* PostgreSQL

Extension to other SQL-talking databases such as MySQL or BigQuery should be
relatively light work. Document stores will be more involved but aren't
necessarily precluded.

## Hardware requirements
The service is intended to be very lightweight. All query results are streamed
directly from the database to the client to speed responses and reduce memory
consumption. I would recommend starting with a 128mb heap and seeing how far
that gets you.

## Prerequisites

To run locally, you will need [Leiningen][] 2.0.0 or later.

[leiningen]: https://github.com/technomancy/leiningen

## Running

To run the application, you must first generate an API schema from your
database. To start, copy the file `example-config.yaml` to `config.yaml`, and
edit it to suit your needs. Once you have it configured to point at the
appropriate database, generate the API spec with

    lein run -c config.yaml --generate > spec.edn

After this runs, you can start the server with

    lein run -c config.yaml -s spec.edn

## Building

PQLServer is deployed as a jar and can be built from source with `leiningen`:

    lein uberjar

The resulting artifact can then be run with,

    java -Xmx128m -jar target/pqlserver-0.1.0-SNAPSHOT-standalone.jar -c config.yaml -s spec.edn

Note that the version number of the artifact may be different, and that setting
the heap size is not strictly required. If not set, it will use the JVM default
of 1/4 system RAM.

## License

Copyright © 2018 Wyatt Alt
