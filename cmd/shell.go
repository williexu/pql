package cmd

import (
	"bytes"
	"fmt"
	"io"
	"io/ioutil"
	"log"
	"os"
	"os/exec"
	"regexp"
	"strings"

	prompt "github.com/c-bata/go-prompt"
	"github.com/docker/docker/pkg/homedir"
	"github.com/mitchellh/go-wordwrap"
	"github.com/spf13/cobra"
	"github.com/wkalt/pql/client"
)

var describeRegex = regexp.MustCompile(`/describe ([a-zA-Z0-9_]+)`)
var namespaceRegex = regexp.MustCompile(`/namespace ([a-zA-Z0-9_]+)`)

func executor(cmd string) {

	if strings.TrimSpace(cmd) == "" {
		return
	}

	c := client.NewClient()

	if strings.HasPrefix(cmd, "/") {
		dispatchMetaCommand(c, cmd)
		return
	}

	// Check if the query will parse; if not just print message and exit.
	if ok, msg := c.Plan(cmd); !ok {
		fmt.Println(msg)
		return
	}

	// Pipe query to less. Block pipe close on closure of ch, which happens when
	// less returns.
	ch := make(chan struct{})
	r, w := io.Pipe()
	go readPipe(r, os.Stdout, ch, "less")
	go spawnQuery(c, w, cmd)
	<-ch
	w.Close()

	recordQuery(cmd)
}

func getHistFile() string {
	return fmt.Sprintf("%s/.pql_history", homedir.Get())
}

func histSearchHandler(histfile string) func(b *prompt.Buffer) {
	return func(b *prompt.Buffer) {
		buf := bytes.NewBufferString("")
		r, w := io.Pipe()

		// Cat the history file to fzf, block on fzf exit, writing output to buf.
		ch := make(chan struct{})
		go readPipe(r, buf, ch, "fzf")
		go writePipe(w, "cat", histfile)
		<-ch
		r.Close()

		// Write fzf selection to the current command line
		b.InsertText(buf.String(), false, false)
	}
}

func readPipe(r *io.PipeReader, w io.Writer, ch chan struct{}, cmd string) {
	defer close(ch)
	c := exec.Command(cmd)
	c.Stdout = w
	c.Stderr = os.Stderr
	c.Stdin = r
	c.Run()
}

func writePipe(w *io.PipeWriter, cmd string, args ...string) {
	c := exec.Command(cmd, args...)
	c.Stdout = w
	c.Run()
	w.Close()
}

func spawnQuery(c client.Client, w *io.PipeWriter, query string) {
	defer w.Close()
	c.Query(query, w)
}

func recordQuery(query string) {
	hist := getHistFile()

	// Scan the current history so we don't store dupes
	currentHist := loadHistFile(hist)
	for _, q := range currentHist {
		if query == strings.TrimRight(q, "\n") {
			return
		}
	}

	// Append the query to histfile
	f, err := os.OpenFile(hist, os.O_WRONLY|os.O_CREATE|os.O_APPEND, 0666)
	if err != nil {
		log.Fatal("Could not open history file for writing:", err)
	}
	defer f.Close()
	_, err = f.WriteString(fmt.Sprintf("%s\n", query))
	if err != nil {
		log.Fatal("Failed to write to history file:", err)
	}
}

func dispatchMetaCommand(c client.Client, cmd string) {
	if cmd == "/describe" {
		fmt.Println(string(c.Describe()))
	} else if ms := describeRegex.FindStringSubmatch(cmd); len(ms) == 2 {
		fmt.Println(string(c.DescribeEntity(ms[1])))
	} else if ms := namespaceRegex.FindStringSubmatch(cmd); len(ms) == 2 {
		fmt.Println("Would switch to:", ms[1], "from", c.Namespace)
	}
}

func completer(t prompt.Document) []prompt.Suggest {
	return []prompt.Suggest{}
}

func loadHistFile(filename string) []string {
	if _, err := os.Stat(filename); os.IsNotExist(err) {
		return []string{}
	}
	bytes, err := ioutil.ReadFile(filename)
	if err != nil {
		log.Fatal("Error reading history file:", err)
	}
	return strings.Split(string(bytes), "\n")
}

var shellCmd = &cobra.Command{
	Use:   "shell",
	Short: "Interactive PQL shell",
	Long:  `Interactively run PQL commands and introspect your data`,
	Run: func(cmd *cobra.Command, args []string) {
		fmt.Println()
		fmt.Println(wordwrap.WrapString(`Welcome to PQL shell. To list available entities, type '/describe'. To list fields for an entity, type '/describe <entity>'. For fuzzy history search, press ctrl-r. To save a query result while viewing it, press 's'. To exit, press ctrl-d.`, 80))

		histfile := getHistFile()
		history := loadHistFile(histfile)

		p := prompt.New(
			executor,
			completer,
			prompt.OptionAddKeyBind(
				prompt.KeyBind{
					Key: prompt.ControlR,
					Fn:  histSearchHandler(histfile),
				},
			),
			prompt.OptionHistory(history),
		)
		p.Run()
	},
}

func init() {
	rootCmd.AddCommand(shellCmd)
}
