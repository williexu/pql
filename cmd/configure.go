package cmd

import (
	"bufio"
	"fmt"
	"log"
	"os"
	"strings"

	"github.com/docker/docker/pkg/homedir"
	"github.com/spf13/cobra"
	"github.com/wkalt/pql/config"
	yaml "gopkg.in/yaml.v2"
)

var configureCmd = &cobra.Command{
	Use:   "configure",
	Short: "Configure the PQL client",
	Long:  `Configure the PQL client with some simple input`,
	Run: func(cmd *cobra.Command, args []string) {
		reader := bufio.NewReader(os.Stdin)
		fmt.Println("Enter a server url:")
		url, err := reader.ReadString('\n')
		if err != nil {
			log.Fatal("Error reading input:", err)
		}

		c := config.Config{
			Server: config.Server{
				URL: strings.TrimRight(url, "\n"),
			},
		}

		data, err := yaml.Marshal(&c)
		if err != nil {
			log.Fatal("Error marshaling config data:", err)
		}

		home := homedir.Get()

		confFile := fmt.Sprintf("%s/.pqlrc", home)

		f, err := os.Create(confFile)
		if err != nil {
			log.Fatal(fmt.Sprintf("Error opening config file %s: %s", confFile, err))
		}

		_, err = f.Write(data)
		if err != nil {
			log.Fatal("Error writing config file:", err)
		}

		fmt.Println("Created ~/.pqlrc")
	},
}

func init() {
	rootCmd.AddCommand(configureCmd)
}
