## pql
Data insights at awesome speed and extreme convenience

Usage:
  pql [command]

Available Commands:
  configure   Configure the PQL client
  describe    Print a description of the configured API
  help        Help about any command
  plan        Print the compiled SQL for a query
  query       Query your PQL server
  shell       Interactive PQL shell

Flags:
  -h, --help   help for pql

Use "pql [command] --help" for more information about a command.


## Installing

`pql shell` requires `fzf`. If you have a working golang installation, you can
install them like this:

    go get github.com/wkalt/pql
    go get github.com/junegunn/fzf

If you are on a Debian-based system, you can install a .deb package from the
releases page, which will install both programs.

If you are on Mac OS, you can download the darwin tarball from the releases
page and place the contents on your path, or you can install golang with

    brew install golang

and use the instructions above.
