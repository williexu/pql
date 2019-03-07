import re
import ijson
import urllib
from iso8601.iso8601 import parse_date


class PQLParseError(Exception):
    pass


def maybe_convert_date(s):
    try:
        return parse_date(s)
    except Exception:
        return s


def convert_dates(obj):
    if isinstance(obj, basestring):
        return maybe_convert_date(obj)
    elif isinstance(obj, list):
        return [convert_dates(x) for x in obj]
    elif isinstance(obj, dict):
        return {k: convert_dates(v) for k, v in obj.items()}
    else:
        return obj


class Client(object):
    def __init__(self, server_url, namespace,
                 version="v1", dates_as_datetimes=False):
        """
        :param server_url: PQL server url with scheme
        :param namespace: desired namespace
        :param version: namespace api version
        :param dates_as_datetimes: attempt to parse date strings as datetimes.
        This increases overall parse time by around 25%
        """
        if '//' not in server_url:
            server_url = '{}{}'.format('http://', server_url)

        if not re.search(r":\d{5}|\d{4}$", server_url):
            raise Exception("Supply a port")

        self.endpoint = "{}/{}/{}/query".format(server_url, namespace, version)
        self.dates_as_datetimes = dates_as_datetimes

    def query(self, pql):
        """Query the PQL server, returning a generator of json objects to allow
        lazy processing of the resultset. Uses urllib for file-like request
        objects; might be a better trick with requests."""
        params = urllib.urlencode({"query": pql})
        resp = urllib.urlopen(self.endpoint + "?{}".format(params))
        if resp.getcode() >= 400:
            raise PQLParseError("\n{}".format(resp.read()))
        else:
            for obj in ijson.items(resp, 'item'):
                yield convert_dates(obj) if self.dates_as_datetimes else obj
