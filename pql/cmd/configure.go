package cmd

import (
	"bufio"
	"fmt"
	"log"
	"os"
	"strings"

	"github.com/spf13/cobra"
	"github.com/wkalt/pql/pql/client"
)

func getServerURL(r *bufio.Reader) string {
	fmt.Println("Enter a server url:")
	url, err := r.ReadString('\n')
	if err != nil {
		log.Fatal("Error reading input:", err)
	}
	return strings.TrimRight(url, "\n")
}

var configureCmd = &cobra.Command{
	Use:   "configure",
	Short: "Configure the PQL client",
	Long:  `Configure the PQL client with some simple input`,
	Run: func(cmd *cobra.Command, args []string) {
		reader := bufio.NewReader(os.Stdin)

		serverURL := getServerURL(reader)

		client := client.Client{
			URL: serverURL,
		}
		client.SetSpec()

		namespaces := client.GetNamespaces()

		if len(namespaces) == 1 {
			fmt.Println("Using namespace:", namespaces[0])
		} else {
			fmt.Println(fmt.Sprintf("Choose a default namespace: %v", namespaces))
			ns, err := reader.ReadString('\n')
			if err != nil {
				log.Fatal("Error reading input:", err)
			}
			client.Namespace = strings.TrimRight(ns, "\n")
		}

		spec := client.Spec[client.Namespace]

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
