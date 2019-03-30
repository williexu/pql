# PQL Suite

This project contains client and server components for a generic database query
interface inspired by the PuppetDB query API and based on [Puppet Query
Language](https://puppet.com/docs/puppetdb/5.1/api/query/v4/pql.html). The
language in this repo retains none of the Puppet-specific aspects of its
predecessor, includes support for a few additional operators, and lacks support
for a few features of the original. As such, PQL in this project is _the
People's Query Language_.

The PQL API server allows you to generate an API specification from one or more
existing Postgres databases and tweak them to suit your needs (similar to
application-level views). The client tools allows you to consume API data from the server
either through a shell (`pql shell`), a command line query tool (`pql query`),
or a python library (`pqlpy`). All responses are streamed, so users can pull
large amounts of data through the API without stressing the server, removing
the need in most cases to paginate or make multiple API calls.

## Client tool

### Installing

If you have a working golang installation, you can install pql with

    go get github.com/wkalt/pql/pql
    go get github.com/junegunn/fzf

`fzf` is a fuzzy file finder that `pql shell` uses for command history search.
If you would prefer a deb package, you can grab one from the releases tab that
will provide both binaries. If you'd rather get binaries, there are binaries
for both `pql` and `fzf` in the release tarball on the same page.

On OSX, you are best off installing golang via homebrew (`brew install golang`)
and following the `go get` instructions above.

### Using

To get started with the tool, connect it to a PQL server by issuing `pql
configure`. This will prompt you to enter a server URL and in cases where your
server provides multiple namespaces, select a namespace. For example,

    $ pql configure
    Enter a server url:
    http://wyattalt.com:3000
    Using namespace: world
    Using API version v1
    Created ~/.pqlrc

Once configured, you can use pql in either a shell mode or as a CLI tool that
can integrate with unix pipes and such.

To view the entities exposed by your namespace, use `pql describe`:

    $ pql describe
    [ "city", "country", "countrylanguage" ]

To view the API fields associated with an entity, use `pql describe <entity>`:

    $ pql describe city
    {
      "id" : "number",
      "name" : "string",
      "countrycode" : null,
      "district" : "string",
      "population" : "number"
    }

To query an entity, the command will look like this:

    $ pql query "city { name ~ 'Francisco' and population > 500000}"
    [
     {
      "countrycode" : "USA",
      "district" : "California",
      "id" : 3805,
      "name" : "San Francisco",
      "population" : 776733
     }
    ]

To see the compiled SQL associated with a query, use `pql plan`:

    $ pql plan "city { name ~ 'Francisco' and population > 500000}"
    {
      "query" : "SELECT city.countrycode, city.district, city.id, city.name, city.population FROM city WHERE (city.name ~ ? AND city.population > ?)",
      "parameters" : [ "Francisco", 500000 ]
    }


As an alternative to the workflow above, you can run `pql shell`, which will
drop you into a shell interface and allow you to run queries without quoting,
retain a searchable query history, and view query results in your pager. See
the `/help` command in PQL shell for usage instructions.

Note that `pql shell` uses `less` as its pager. Since `less` supports
reverse-scrolling, it must buffer all previously seen results in memory. This
means traversing huge (multigigabyte) resultsets in `pql shell` be inadvisable,
although such resultsets can be saved with 's' without issue.

If you need to traverse a huge resultset with the pager (you probably don't),
set the environment variable

    PQL_PAGER=more

This will allow you to browse, but not save, an arbitrary resultset with
minimal memory overhead.

#### Newline-delimited output
Newline-delimited output is available for the query and shell subcommands. To
use it, supply the flag `-n`:

    $ pql query -n "city{limit 5}"
    {"countrycode":"AFG","district":"Kabol","id":1,"name":"Kabul","population":1780000}
    {"countrycode":"AFG","district":"Qandahar","id":2,"name":"Qandahar","population":237500}
    {"countrycode":"AFG","district":"Herat","id":3,"name":"Herat","population":186800}
    {"countrycode":"AFG","district":"Balkh","id":4,"name":"Mazar-e-Sharif","population":127800}
    {"countrycode":"NLD","district":"Noord-Holland","id":5,"name":"Amsterdam","population":731200}

### Language features

PQL supports the following operators:

Comparison for strings and numbers:
* `<=`
* `<`
* `>=`
* `>`
* `=`
* `!=`

Regular expressions:
* Case-sensitive regex: `~`
* Negated case-sensitive regex: `!~`
* Case-insensitive regex: `~*`
* Negated case-insensitive regex: `!~*`

Null checks:
* `is null`
* `is not null`

Additional supported language concepts include:

* `limit`, `offset`, ascending/descending `order by`

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

* Group by and aggregate functions:

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

* JSON object descendence ('dot notation')

    $ pql query "country { attributes.foo.bar = 'baz'}"


## Python client
This repo also includes a python client, `pqlpy`. This is an extremely simple
`urllib` wrapper that exposes a PQL server response as a generator. To
use it,

    from pqlpy import Client
    c = Client("http://wyattalt.com:3000", "world")
    results = c.query("country{}")
    for record in results:
        print("record: ", record)

## Server

`pqlserver` is the server component. It is responsible for housing the API
specification, compiling PQL to SQL, maintaining connection pools to configured
databases, and streaming responses from the database to connected clients.

### Running locally

To run locally, you will need [Leiningen][] 2.0.0 or later.

[leiningen]: https://github.com/technomancy/leiningen

Once lein is installed, get the server running by copying `example-config.yaml`
to `config.yaml`, editing it to point at your database(s), and launching the
service with

    lein run -c config.yaml

This will deploy pqlserver with an API that matches the schema of your
database. If you require a more sophisticated mapping, generate an API spec
from the database with

    lein run -c config.yaml --generate-spec spec.edn

Edit the resulting file to meet your needs, and run the service with

    lein run -c config.yaml -s spec.edn

The API is specified in in [edn](https://github.com/edn-format/edn).  Aside
from the lists of projected columns, the part that needs to be modified is the
value keyed "base". It represents a SQL query in
[HoneySQL](https://github.com/jkk/honeysql). To modify it appropriately, you
will need to read up a bit on HoneySQL.

A major limitation of the current specification format is that it only supports
entities that HoneySQL can express in edn-compatible datastructures, which
rules out the possibilty of using vendor-specific functionality in API
definitions, such as Postgres JSON aggregate functions. In the future it may be
possible to support entities described by SQL strings, which would fix this
issue.

### Running from a jar

PQL is deployed as a jar. To build the jar locally, you can run

    lein uberjar

from the server directory. The resulting artifact can be run with

    java -Xmx128m -jar target/pqlserver-0.1.0-SNAPSHOT-standalone.jar -c config.yaml -s spec.edn

Note that the version number of the artifact may be different, and that setting
the heap size is not strictly required. If not set, it will use the JVM default
of 1/4 system RAM. Importantly, this can cause issues on containerized
environments where the JVM will base the calculation on the host's capacity.
128m is probably a pretty good place to start.

## Administration

### Hardware requirements
The service is intended to be very lightweight. All query results are streamed
directly from the database to the client to speed responses and reduce memory
consumption. I would recommend starting with a 128mb heap and seeing how far
that gets you.

### Supported databases

The following databases are supported:

* PostgreSQL

Extension to other SQL-talking databases such as MySQL or BigQuery should be
relatively light work. Document stores will be more involved but aren't
necessarily precluded.

### API Versioning
Consumers may find need to make breaking API changes. The pqlserver API
specification format supports multiple specs, and nests them under version keys
like :v1, which is the default for generated specs. Client tools provide
mechanisms to select an API version from those available. If you need to make
breaking changes, the typical workflow would be to duplicate your existing
schema, modulo motivating specification changes, under an incremented version
key. Deprecation and/or retirement policies are of course your business.

### Metrics
The service has a `/metrics` endpoint with detailed information about the state
of the JVM and request counts/timings/etc. You can access it with curl, e.g

    curl -X GET http://localhost:3000/metrics

See [this gist](https://gist.github.com/wkalt/808d414f6e08aeea9355b246608b8bb0)
for an example of what you get.

These metrics can also be exposed over JMX with the right choice of java args.
An insecure example is

    java -Dcom.sun.management.jmxremote \
    -Dcom.sun.management.jmxremote.port=1099 \
    -Dcom.sun.management.jmxremote.ssl=false
    -Djava.rmi.server.hostname=localhost \
    -Dcom.sun.management.jmxremote.local.only=false \
    -Dcom.sun.management.jmxremote.authenticate=false \
    -jar target/pqlserver-0.1.0-SNAPSHOT-standalone.jar -c config.yaml -s spec.edn

## Building and testing
Client and server tests are all run from clojure, for the convenience of
testing clients against a real and up-to-date server. To run the tests, run

    make test

from the project root. To build all deployment artifacts, run

    make build

to install the client tool, run

    make install

## License
Copyright © 2018 Wyatt Alt
