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
	"github.com/spf13/viper"
	"github.com/wkalt/pql/pql/client"
)

var describeRegex = regexp.MustCompile(`/e ([a-zA-Z0-9_]+)`)
var namespaceRegex = regexp.MustCompile(`/j ([a-zA-Z0-9_]+)`)

func makeExecutor(c *client.Client) func(string) {
	return func(cmd string) {
		if strings.TrimSpace(cmd) == "" {
			return
		}

		if strings.HasPrefix(cmd, "/") {
			dispatchMetaCommand(c, cmd)
			return
		}

		// Check if the query will parse; if not just print message and exit.
		if ok, msg := c.Plan(cmd); !ok {
			fmt.Println(string(msg))
			return
		}

		// Pipe query to less. Block pipe close on closure of ch, which happens when
		// less returns.
		ch := make(chan struct{})
		r, w := io.Pipe()

		epager := os.Getenv("PQL_PAGER")
		var pager string
		switch epager {
		case "":
			pager = "less"
		default:
			pager = epager
		}
		go readPipe(r, os.Stdout, ch, pager)
		go spawnQuery(c, w, cmd)
		<-ch
		w.Close()

		recordQuery(cmd)
	}
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

func spawnQuery(c *client.Client, w *io.PipeWriter, query string) {
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

func dispatchMetaCommand(c *client.Client, cmd string) {
	if cmd == "/help" {
		printHelpCommand()
	} else if cmd == "/e" {
		fmt.Println(string(c.Describe()))
	} else if ms := describeRegex.FindStringSubmatch(cmd); len(ms) == 2 {
		fmt.Println(string(c.DescribeEntity(ms[1])))
	} else if cmd == "/ls" {
		printAvailableNamespaces(c)
	} else if ms := namespaceRegex.FindStringSubmatch(cmd); len(ms) == 2 {
		err := c.SetNamespace(ms[1])
		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println(fmt.Sprintf("Switched to namespace '%s'", ms[1]))
		}
	} else if strings.HasPrefix(cmd, "/plan ") {
		q := strings.SplitN(cmd, " ", 2)[1]
		_, plan := c.Plan(q)
		fmt.Println(string(plan))
	} else {
		fmt.Println(fmt.Sprintf("Command '%s' not found", cmd))
	}
}

func printAvailableNamespaces(c *client.Client) {
	fmt.Println(fmt.Sprintf("Available namespaces: %+v", c.GetNamespaces()))
}

func printHelpCommand() {
	str := `Every command in the prompt is interpreted as a PQL query, except the following metacommands:

/ls : list available namespaces
/j <namespace> : connect to a new namespace
/e : list entities in the current namespace
/e <entity> : list available fields for an entity
/plan <query>: display the compiled SQL for a query without executing it
/help: display this help message

When viewing a query result in less, you can exit the pager with 'q' and save
the query result with 's'.

Additional shell features:
Fuzzy history search with ctrl-r
Exit the shell with ctrl-d`

	fmt.Println(wordwrap.WrapString(str, 80))
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
		fmt.Println(wordwrap.WrapString(`Welcome to PQL shell. Enter /help to view available commands.`, 80))

		histfile := getHistFile()
		history := loadHistFile(histfile)

		opts := client.Options{
			NewlineDelimited: newlineDelimited,
		}

		c := client.NewClient(opts)

		livePrefix := func() (string, bool) {
			return fmt.Sprintf("%s=> ", c.Namespace), true
		}

		p := prompt.New(
			makeExecutor(c),
			completer,
			prompt.OptionAddKeyBind(
				prompt.KeyBind{
					Key: prompt.ControlR,
					Fn:  histSearchHandler(histfile),
				},
			),
			prompt.OptionHistory(history),
			prompt.OptionLivePrefix(livePrefix),
		)
		p.Run()
	},
}

func init() {
	shellCmd.PersistentFlags().BoolVarP(
		&newlineDelimited, "newline-delimited", "n", false, "return newline-delimited JSON")
	viper.BindPFlag("newline-delimited", queryCmd.PersistentFlags().Lookup("newline-delimited"))

	rootCmd.AddCommand(shellCmd)
}
