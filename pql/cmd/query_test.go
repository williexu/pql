package cmd

import (
	"bytes"
	"encoding/json"
	"reflect"
	"testing"
)

func TestQuery(t *testing.T) {

	c := makeClient()

	// Query with no args
	buf := bytes.NewBufferString("")
	query(c, buf)
	if buf.String() != "Supply a query" {
		t.Error("Unexpected response for query with no result")
	}

	// Query with results
	buf = bytes.NewBufferString("")
	query(c, buf, "pets { name ~ 'ack' }")

	results := []map[string]string{}
	err := json.Unmarshal(buf.Bytes(), &results)
	if err != nil {
		t.Error("Unexpected error unmarshalling query response", err)
	}

	expected := []map[string]string{
		map[string]string{
			"name":  "jack",
			"owner": "abraham lincoln",
		},
	}

	if !reflect.DeepEqual(expected, results) {
		t.Error("Unexpected result from query")
	}
}
