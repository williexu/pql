package cmd

import (
	"bufio"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"strings"

	"github.com/spf13/cobra"
	"github.com/wkalt/pql/client"
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

		serverURL := strings.TrimRight(url, "\n")

		client := client.Client{
			URL: serverURL,
		}

		m := make(map[string]interface{})

		err = json.Unmarshal(client.DescribeAll(), &m)
		if err != nil {
			log.Fatal("Error gathering API spec:", err)
		}

		availableVersions := []string{}
		for k := range m {
			availableVersions = append(availableVersions, k)
		}

		if len(availableVersions) == 1 {
			fmt.Println(fmt.Sprintf("Using API version %s", availableVersions[0]))
			client.APIVersion = availableVersions[0]
		} else {
			fmt.Println(fmt.Sprintf("Select a version: %v", availableVersions))
			version, err := reader.ReadString('\n')
			if err != nil {
				log.Fatal("Error reading input:", err)
			}
			client.APIVersion = strings.TrimRight(version, "\n")
		}

		client.WriteConfig()
	},
}

func init() {
	rootCmd.AddCommand(configureCmd)
}
