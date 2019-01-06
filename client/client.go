package client

import (
	"bufio"
	"fmt"
	"io"
	"io/ioutil"
	"log"
	"net/http"
	"os"

	"github.com/docker/docker/pkg/homedir"
	yaml "gopkg.in/yaml.v2"
)

type ClientSpec map[string]interface{}

// Client represents a pqlserver client
type Client struct {
	URL        string     `yaml:"url"`
	APIVersion string     `yaml:"version"`
	Namespace  string     `yaml:"namespace"`
	Spec       ClientSpec `yaml:"body"`
}

// Describe returns a description of the API schema
func (c *Client) DescribeEntity(entity string) []byte {
	url := fmt.Sprintf("%s/%s/%s/describe/%s",
		c.URL, c.Namespace, c.APIVersion, entity)
	resp, err := http.Get(url)
	if err != nil {
		log.Fatal("Error getting API metadata:", err)
	}
	defer resp.Body.Close()
	body, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		log.Fatal("Error reading response body:", err)
	}
	return body
}

// Describe returns a description of the API schema
func (c *Client) Describe() []byte {
	url := fmt.Sprintf("%s/%s/%s/describe", c.URL, c.Namespace, c.APIVersion)
	resp, err := http.Get(url)
	if err != nil {
		log.Fatal("Error getting API metadata:", err)
	}
	defer resp.Body.Close()
	body, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		log.Fatal("Error reading response body:", err)
	}
	return body
}

// DescribeAll returns a description of the API schema
func (c *Client) DescribeAll() []byte {
	url := fmt.Sprintf("%s/describe-all", c.URL)
	resp, err := http.Get(url)
	if err != nil {
		log.Fatal("Error getting API metadata:", err)
	}
	defer resp.Body.Close()
	body, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		log.Fatal("Error reading response body:", err)
	}
	return body
}

// Plan returns a representation of the SQL to be executed.
func (c *Client) Plan(pql string) (bool, string) {
	url := fmt.Sprintf("%s/%s/%s/plan", c.URL, c.Namespace, c.APIVersion)
	client := &http.Client{}
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		log.Fatalf("Error making GET request to %s: %s", url, err)
	}
	params := req.URL.Query()
	params.Add("query", pql)
	req.URL.RawQuery = params.Encode()
	resp, err := client.Do(req)
	if err != nil {
		log.Fatalf("Error making GET request to %s: %s", url, err)
	}
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
	client := &http.Client{}
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		log.Fatalf("Error making GET request to %s: %s", url, err)
	}

	params := req.URL.Query()
	params.Add("query", pql)
	req.URL.RawQuery = params.Encode()

	resp, err := client.Do(req)
	if err != nil {
		log.Fatalf("Error making GET request to %s: %s", url, err)
	}
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

	home := homedir.Get()
	conf := fmt.Sprintf("%s/.pqlrc", home)
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

// NewClient constructs a client if the config exists.
func NewClient() *Client {
	home := homedir.Get()
	conf := fmt.Sprintf("%s/.pqlrc", home)
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
	return c
}

func main() {
	fmt.Println("vim-go")
}
