package cmd

import (
	"fmt"

	"github.com/spf13/cobra"
	"github.com/wkalt/pql/client"
)

// describeCmd represents the describe command
var describeCmd = &cobra.Command{
	Use:   "describe",
	Short: "Print a description of the configured API",
	Long: `Print a description of the configured API. For example,
	pql describe
	`,
	Run: func(cmd *cobra.Command, args []string) {
		c := client.NewClient()
		fmt.Println(string(c.Describe()))
	},
}

func init() {
	rootCmd.AddCommand(describeCmd)
}
