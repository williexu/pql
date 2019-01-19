import os
import re
import unittest
import datetime

from pqlpy import Client

SERVER_URL = os.environ["PQL_TEST_SERVER_URL"]


class TestClientConnection(unittest.TestCase):

    def test_basic_query(self):
        c = Client(SERVER_URL, "test_1")
        results = c.query("people{}")
        nresults = 0
        for x in results:
            nresults += 1
        self.assertEqual(2, nresults)

    def test_improper_urls(self):

        url_without_scheme = re.sub("http://", "", SERVER_URL)
        c = Client(url_without_scheme, "test_1")
        results = c.query("people{}")
        nresults = 0
        for x in results:
            nresults += 1
        self.assertEqual(2, nresults)

        url_without_port = re.sub(r":\d{4}|\d{5}$", "", SERVER_URL)

        with self.assertRaises(Exception):
            c = Client(url_without_port, "test_1")

    def test_dates_as_datetimes(self):
        c = Client(SERVER_URL, "test_1")
        results = c.query("people{}")
        self.assertTrue(isinstance(results.next()['birthday'], basestring))

        c = Client(SERVER_URL, "test_1", dates_as_datetimes=True)
        results = c.query("people{}")
        self.assertTrue(
            isinstance(results.next()['birthday'], datetime.datetime))


if __name__ == '__main__':
    unittest.main()
