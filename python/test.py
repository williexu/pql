import os
import re
from pqlpy import Client
import unittest

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


if __name__ == '__main__':
    unittest.main()
