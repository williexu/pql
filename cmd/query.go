package cmd

import (
	"bufio"
	"fmt"
	"io/ioutil"
	"log"
	"net/http"
	"os"

	"github.com/docker/docker/pkg/homedir"
	"github.com/spf13/cobra"
	"github.com/wkalt/pql/config"
	yaml "gopkg.in/yaml.v2"
)

var queryCmd = &cobra.Command{
	Use:   "query",
	Short: "Query your PQL server",
	Long: `Query the PQL server. For example,
pql query "people { name ~ 'foo' }"
pql query "people { name ~ 'foo' and age > 30 }"
pql query "people { name ~ 'foo' and age > 30 limit 100}"`,

	Run: func(cmd *cobra.Command, args []string) {
		home := homedir.Get()
		confFile := fmt.Sprintf("%s/.pqlrc", home)

		query := args[0]

		if _, err := os.Stat(confFile); os.IsNotExist(err) {
			log.Fatal("Run `pql configure` to generate ~/.pqlrc")
		}

		confBytes, err := ioutil.ReadFile(confFile)
		if err != nil {
			log.Fatal("Error reading config file:", err)
		}

		c := config.Config{}
		err = yaml.Unmarshal(confBytes, &c)
		if err != nil {
			log.Fatal("Error parsing config file:", err)
		}

		if query == "" {
			log.Fatal("Supply a query")
		}

		url := fmt.Sprintf("%s/query", c.Server.URL)
		client := &http.Client{}
		req, err := http.NewRequest("GET", url, nil)
		if err != nil {
			log.Fatalf("Error making GET request to %s: %s", url, err)
		}

		params := req.URL.Query()
		params.Add("query", query)
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
			log.Fatal("Server error:", string(body))

		case resp.StatusCode == 200:
			stdout := bufio.NewWriter(os.Stdout)
			defer stdout.Flush()
			buf := bufio.NewReader(resp.Body)
			buf.WriteTo(stdout)
		}
	},
}

func init() {
	rootCmd.AddCommand(queryCmd)
}
