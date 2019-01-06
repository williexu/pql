package client

import (
	"bufio"
	"encoding/json"
	"fmt"
	"io"
	"io/ioutil"
	"log"
	"net/http"
	"os"

	"github.com/docker/docker/pkg/homedir"
	yaml "gopkg.in/yaml.v2"
)

// Client represents a pqlserver client
type Client struct {
	URL        string                            `yaml:"url"`
	APIVersion string                            `yaml:"version"`
	Namespace  string                            `yaml:"namespace"`
	Spec       map[string]map[string]interface{} `yaml:"-"`
}

func getConfig() string {
	return fmt.Sprintf("%s/.pqlrc", homedir.Get())
}

func request(url string) []byte {
	resp, err := http.Get(url)
	if err != nil {
		log.Fatalf("Error making request to %s: %s", url, err)
	}
	defer resp.Body.Close()
	body, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		log.Fatalf("Error reading response body of request to %s: %s", url, err)
	}
	return body
}

func makeRequest(url string, pmap map[string]string) *http.Response {
	c := &http.Client{}
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		log.Fatalf("Error building GET request to %s: %s", url, err)
	}
	params := req.URL.Query()
	for k, v := range pmap {
		params.Add(k, v)
	}
	req.URL.RawQuery = params.Encode()
	resp, err := c.Do(req)
	if err != nil {
		log.Fatalf("Error making GET request to %s: %s", url, err)
	}
	return resp
}

// Describe returns a description of the API schema
func (c *Client) DescribeEntity(entity string) []byte {
	url := fmt.Sprintf("%s/%s/%s/describe/%s", c.URL, c.Namespace, c.APIVersion, entity)
	bytes := request(url)
	return bytes
}

// Describe returns a description of the API schema
func (c *Client) Describe() []byte {
	url := fmt.Sprintf("%s/%s/%s/describe", c.URL, c.Namespace, c.APIVersion)
	bytes := request(url)
	return bytes
}

// Plan returns a representation of the SQL to be executed.
func (c *Client) Plan(pql string) (bool, string) {
	url := fmt.Sprintf("%s/%s/%s/plan", c.URL, c.Namespace, c.APIVersion)
	resp := makeRequest(url, map[string]string{"query": pql})
	defer resp.Body.Close()
	body, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		log.Fatal("Error reading response body:", err)
	}
	switch {
	case resp.StatusCode != 200:
		return false, string(body)
	default:
		return true, string(body)
	}
}

// Query the PQL server
func (c *Client) Query(pql string, out io.Writer) {
	url := fmt.Sprintf("%s/%s/%s/query", c.URL, c.Namespace, c.APIVersion)
	resp := makeRequest(url, map[string]string{"query": pql})
	defer resp.Body.Close()

	switch {
	case resp.StatusCode >= 500:
		body, err := ioutil.ReadAll(resp.Body)
		if err != nil {
			log.Fatal("Error reading 500 response body:", err)
		}
		fmt.Println("Server error:", string(body))
		os.Exit(1)

	case resp.StatusCode >= 400:
		body, err := ioutil.ReadAll(resp.Body)
		if err != nil {
			log.Fatal("Error reading 400 response body:", err)
		}
		fmt.Println(string(body))
		os.Exit(1)

	case resp.StatusCode == 200:
		buf := bufio.NewReader(resp.Body)
		buf.WriteTo(out)
		out.Write([]byte("\n"))
	}
}

// WriteConfig writes client attributes to the user's config file
func (c *Client) WriteConfig() {
	data, err := yaml.Marshal(&c)
	if err != nil {
		log.Fatal("Error marshaling config data:", err)
	}
	conf := getConfig()
	f, err := os.Create(conf)
	if err != nil {
		log.Fatal(fmt.Sprintf("Error creating config file %s: %s", conf, err))
	}
	_, err = f.Write(data)
	if err != nil {
		log.Fatal("Error writing config file:", err)
	}
	fmt.Println("Created ~/.pqlrc")
}

// SetSpec gets the specification for a client
func (c *Client) SetSpec() {
	url := fmt.Sprintf("%s/describe-all", c.URL)
	bytes := request(url)
	m := make(map[string]map[string]interface{})
	err := json.Unmarshal(bytes, &m)
	if err != nil {
		log.Fatal("Error gathering API spec:", err)
	}
	c.Spec = m
}

// NewClient constructs a client if the config exists.
func NewClient() *Client {
	conf := getConfig()
	if _, err := os.Stat(conf); os.IsNotExist(err) {
		log.Fatal("Run `pql configure` to generate ~/.pqlrc")
	}
	confBytes, err := ioutil.ReadFile(conf)
	if err != nil {
		log.Fatal("Error reading config file:", err)
	}

	c := &Client{}
	err = yaml.Unmarshal(confBytes, &c)
	if err != nil {
		log.Fatal("Error parsing config file:", err)
	}
	c.SetSpec()
	return c
}
