import os
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


if __name__ == '__main__':
    unittest.main()
