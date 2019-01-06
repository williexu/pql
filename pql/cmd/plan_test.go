package cmd

import (
	"encoding/json"
	"testing"
)

func TestPlan(t *testing.T) {

	c := makeClient()

	// No query supplied
	msg, ok := plan(c)
	if ok {
		t.Errorf("Unexpected successful response from plan with no query")
	}

	if msg != "Supply a query" {
		t.Errorf("Unexpected message from plan with no query")
	}

	msg, ok = plan(c, "people[name]{}")
	if !ok {
		t.Errorf("Unexpected failure response from plan")
	}

	m := make(map[string]interface{})
	err := json.Unmarshal([]byte(msg), &m)
	if err != nil {
		t.Errorf("Unexpected error unmarshalling plan json: %s", err)
	}

	if m["query"] != "SELECT people.name FROM people" {
		t.Errorf("Unexpected plan response for people[name]{}")
	}
}
