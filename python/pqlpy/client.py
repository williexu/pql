import ijson
import urllib


class Client(object):
    def __init__(self, server_url, version="v1"):
        self.endpoint = "{}/query/{}".format(server_url, version)

    def query(self, pql):
        """Query the PQL server, returning a generator of json objects to allow
        lazy processing of the resultset. Uses urllib for file-like request
        objects; might be a better trick with requests."""
        params = urllib.urlencode({"query": pql})
        request = urllib.urlopen(self.endpoint + "?{}".format(params))
        for obj in ijson.items(request, 'item'):
            yield obj
