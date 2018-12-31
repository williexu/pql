## pqlpy

Python client library for pqlserver.

### Usage
`pqlpy` includes only one class, `Client`. Initialize it with your API server
URL and version:

    from pqlpy import Client
    client = Client("http://localhost:3000", "v1")

Execute queries with the `query` method. This will return a generator, allowing
you to lazily pull results from the server:

    records = client.Query("people{}")

### Building

Build and install with virtualenv and make:

    virtualenv venv
    source venv/bin/activate
    pip install -r requirements.txt
    make install
