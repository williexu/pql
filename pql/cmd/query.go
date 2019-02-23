package cmd

import (
	"bufio"
	"fmt"
	"io"
	"os"

	"github.com/spf13/cobra"
	"github.com/spf13/viper"
	"github.com/wkalt/pql/pql/client"
)

var queryCmd = &cobra.Command{
	Use:   "query",
	Short: "Query your PQL server",
	Long: `Query the PQL server. For example,
pql query "people { name ~ 'foo' }"
pql query "people { name ~ 'foo' and age > 30 }"
pql query "people { name ~ 'foo' and age > 30 limit 100}"`,

	Run: func(cmd *cobra.Command, args []string) {
		opts := client.Options{
			NewlineDelimited: newlineDelimited,
		}

		c := client.NewClient(opts)
		out := bufio.NewWriter(os.Stdout)
		defer out.Flush()
		query(c, out, args...)
	},
}

func query(c *client.Client, out io.Writer, args ...string) {
	if len(args) == 0 {
		fmt.Fprint(out, "Supply a query")
		return
	}
	query := args[0]
	c.Query(query, out)
}

func init() {
	queryCmd.PersistentFlags().BoolVarP(
		&newlineDelimited, "newline-delimited", "n", false, "return newline-delimited JSON")
	viper.BindPFlag("newline-delimited", queryCmd.PersistentFlags().Lookup("newline-delimited"))

	rootCmd.AddCommand(queryCmd)
}
