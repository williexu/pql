test:
	cd server && lein test && cd -

.PHONY: build
build: server client

.PHONY: server
server:
	mkdir -p build
	cd server && lein uberjar && cd -
	cp server/target/pqlserver-0.1.0-SNAPSHOT-standalone.jar build/

.PHONY: client
client:
	mkdir -p build
	gox -osarch="linux/amd64 darwin/amd64" -output "build/{{.Dir}}_{{.OS}}_{{.Arch}}" ./...
	GOBIN=$(CURDIR)/build go get github.com/junegunn/fzf
