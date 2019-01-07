package cmd

import (
	"bytes"
	"encoding/json"
	"reflect"
	"strings"
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

	// Query for an entity that doesn't exist
	buf = bytes.NewBufferString("")
	query(c, buf, "foobar{}")
	exp := `Unrecognized entity 'foobar'. Available entities: ["people" "pets"]`

	if strings.TrimRight(buf.String(), "\n") != exp {
		t.Error("Unexpected result querying for entity that doesn't exist:", exp)
	}

}
