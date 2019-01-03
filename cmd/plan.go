package cmd

import (
	"github.com/spf13/cobra"
	"github.com/wkalt/pql/client"
	"log"
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
		query := args[0]
		if query == "" {
			log.Fatal("Supply a query")
		}
		c := client.NewClient()
		c.Plan(query)
	},
}

func init() {
	rootCmd.AddCommand(planCmd)
}
