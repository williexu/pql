package cmd

import (
	"fmt"
	"github.com/spf13/cobra"
	"github.com/wkalt/pql/client"
	"os"
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
		if len(args) == 0 {
			fmt.Println("Supply a query")
			os.Exit(1)
		}
		query := args[0]
		c := client.NewClient()
		c.Plan(query)
	},
}

func init() {
	rootCmd.AddCommand(planCmd)
}
