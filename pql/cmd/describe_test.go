package cmd

import (
	"encoding/json"
	"reflect"
	"testing"
)

func TestDescribe(t *testing.T) {
	c := makeClient()

	// No args; will describe available entities
	result := describe(c)

	s := []string{}
	err := json.Unmarshal([]byte(result), &s)
	if err != nil {
		t.Errorf("Failed to deserialize describe response")
	}

	if !reflect.DeepEqual(s, []string{"people", "pets"}) {
		t.Errorf("Unexpected entities in describe response")
	}

	// Describe an entity
	result = describe(c, "pets")
	m := make(map[string]string)
	err = json.Unmarshal([]byte(result), &m)
	if err != nil {
		t.Error("Failed to deserialize describe pets response", err)
	}

	if !reflect.DeepEqual(m, map[string]string{
		"owner": "string", "name": "string"}) {
		t.Errorf("Unexpected result from describe pets: %+v", m)
	}

	// Describe an entity that doesn't exist
	result = describe(c, "foobar")
	if result != "Unrecognized entity 'foobar'" {
		t.Error("Unexpected response describing for entity that doesn't exist")
	}
}
