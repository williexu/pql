test:
	cd server && lein test && cd -

.PHONY: build
build: clean server client

.PHONY: server
server:
	mkdir -p build
	cd server && lein uberjar && cd -
	cp server/target/pqlserver-${rel}-SNAPSHOT-standalone.jar build/

.PHONY: client
client:
	mkdir -p build
	gox -osarch="linux/amd64" -output "build/{{.Dir}}" ./...
	GOBIN=$(CURDIR)/build go get github.com/junegunn/fzf
	cd build && fpm -s dir -t deb -n pql -v ${rel} . ./pql=/usr/bin/ ./fzf=/usr/bin/ && cd -
	tar cvf build/pql-${rel}.tar build/fzf build/pql

.PHONY: install
install:
	cd pql && go install && cd -

.PHONY: clean
clean:
	rm -rf build/*
