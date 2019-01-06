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

		m := make(map[string]map[string]interface{})

		err = json.Unmarshal(client.DescribeAll(), &m)
		if err != nil {
			log.Fatal("Error gathering API spec:", err)
		}

		availableNamespaces := []string{}
		for k := range m {
			availableNamespaces = append(availableNamespaces, k)
		}

		if len(availableNamespaces) == 1 {
			fmt.Println("Using namespace:", availableNamespaces[0])
		} else {
			fmt.Println(fmt.Sprintf("Choose a namespace: %v", availableNamespaces))
			ns, err := reader.ReadString('\n')
			if err != nil {
				log.Fatal("Error reading input:", err)
			}
			client.Namespace = strings.TrimRight(ns, "\n")
		}

		fmt.Println(fmt.Sprintf("MAP IS %+v", m))
		fmt.Println(fmt.Sprintf("CLIENT NS: %s", client.Namespace))
		fmt.Println(fmt.Sprintf("MAPVAL: %s", m[client.Namespace]))

		spec := m[client.Namespace]

		availableVersions := []string{}
		for k := range spec {
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
