package cmd

import (
	"fmt"
	"os"

	"github.com/spf13/cobra"
	"github.com/spf13/viper"
)

// Commandline options
var newlineDelimited bool

var rootCmd = &cobra.Command{
	Use:   "pql",
	Short: "Command line client for PQL server",
	Long:  `Data insights at awesome speed and extreme convenience`,
}

// Execute the command
func Execute() {
	if err := rootCmd.Execute(); err != nil {
		fmt.Println(err)
		os.Exit(1)
	}
}

func init() {
	cobra.OnInitialize(initConfig)
}

func initConfig() {
	viper.AutomaticEnv()
}
