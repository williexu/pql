package:
	mkdir -p build
	go build -o build/pql main.go
	GOBIN=$(CURDIR)/build go get github.com/junegunn/fzf
	fpm -s dir -t deb -n pql -v 0.0.6 build/ build/pql=/usr/bin/ build/fzf=/usr/bin/
