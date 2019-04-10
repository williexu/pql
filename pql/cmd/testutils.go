package cmd

import (
	"os"

	"github.com/wkalt/pql/pql/client"
)

func makeClient() *client.Client {
	serverURL := os.Getenv("PQL_TEST_SERVER_URL")
	c := client.Client{
		URL:        serverURL,
		APIVersion: "v1",
		Namespace:  "test_1",
	}
	return &c
}
