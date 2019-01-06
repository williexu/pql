package cmd

import (
	"fmt"
	"os"

	"github.com/spf13/cobra"
	"github.com/wkalt/pql/pql/client"
)

var planCmd = &cobra.Command{
	Use:   "plan",
	Short: "Print the compiled SQL for a query",
	Long: `Query the PQL server for the compiled SQL of a query. For example.
	$ pql plan "people{ name ~ 'foo'}"
	{
		"query" : "SELECT people.age, people.attributes, people.name, people.siblings, people.street_address, people.t FROM people WHERE people.name ~ ?",
		"parameters" : [ "foo" ]
	}`,

	Run: func(cmd *cobra.Command, args []string) {
		c := client.NewClient()
		result, ok := plan(c, args...)
		if !ok {
			fmt.Println(result)
			os.Exit(1)
		}
		fmt.Println(result)
	},
}

func plan(c *client.Client, args ...string) (string, bool) {
	if len(args) == 0 {
		return "Supply a query", false
	}
	query := args[0]
	_, body := c.Plan(query)
	return string(body), true
}

func init() {
	rootCmd.AddCommand(planCmd)
}
