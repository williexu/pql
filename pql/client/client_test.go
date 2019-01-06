package client

import (
	"encoding/json"
	"os"
	"reflect"
	"testing"
)

func makeClient() *Client {
	serverURL := os.Getenv("PQL_TEST_SERVER_URL")
	c := Client{
		URL:        serverURL,
		APIVersion: "v1",
		Namespace:  "test_1",
	}
	c.SetSpec()
	return &c
}

func TestNamespaceSwitching(t *testing.T) {
	c := makeClient()
	result := []string{}
	bytes := c.Describe()
	err := json.Unmarshal(bytes, &result)
	if err != nil {
		t.Errorf("Error unmarshalling DescribeEntity response: %s", err)
	}
	expected := []string{"people", "pets"}
	if !reflect.DeepEqual(expected, result) {
		t.Errorf("Unexpected keys from DescribeEntity. Got: %+v Expected %+v",
			result, expected)
	}

	c.SetNamespace("test_2")
	result = []string{}
	bytes = c.Describe()
	err = json.Unmarshal(bytes, &result)
	if err != nil {
		t.Errorf("Error unmarshalling DescribeEntity response: %s", err)
	}
	expected = []string{"cars", "people", "pets"}
	if !reflect.DeepEqual(expected, result) {
		t.Errorf("Unexpected keys from DescribeEntity. Got: %+v Expected %+v",
			result, expected)
	}
}
