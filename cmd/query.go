package cmd

import (
	"fmt"
	"os"

	"github.com/spf13/cobra"
	"github.com/wkalt/pql/client"
)

var queryCmd = &cobra.Command{
	Use:   "query",
	Short: "Query your PQL server",
	Long: `Query the PQL server. For example,
pql query "people { name ~ 'foo' }"
pql query "people { name ~ 'foo' and age > 30 }"
pql query "people { name ~ 'foo' and age > 30 limit 100}"`,

	Run: func(cmd *cobra.Command, args []string) {
		if len(args) == 0 {
			fmt.Println("Supply a query")
			os.Exit(1)
		}
		query := args[0]
		c := client.NewClient()
		c.Query(query)
	},
}

func init() {
	rootCmd.AddCommand(queryCmd)
}
