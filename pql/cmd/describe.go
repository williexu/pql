package cmd

import (
	"fmt"

	"github.com/spf13/cobra"
	"github.com/wkalt/pql/pql/client"
)

// describeCmd represents the describe command
var describeCmd = &cobra.Command{
	Use:   "describe",
	Short: "Print a description of the configured API",
	Long: `Print a description of the configured API. To print available entities,
	'pql describe'. To print fields for an entity, 'pql describe <entity>'`,
	Run: func(cmd *cobra.Command, args []string) {
		c := client.NewClient()
		if len(args) == 0 {
			fmt.Println(string(c.Describe()))
		} else {
			fmt.Println(string(c.DescribeEntity(args[0])))
		}
	},
}

func init() {
	rootCmd.AddCommand(describeCmd)
}
