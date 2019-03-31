test:
	cd server && lein test && cd -

.PHONY: build
build: clean server client pqlpy docker

.PHONY: pqlpy
pqlpy: builddir
	pip wheel ./python -w build

.PHONY: server
server:
	rm -rf server/target
	cd server && lein uberjar && cd -
	cp server/target/pqlserver-*-SNAPSHOT-standalone.jar build/

.PHONY: docker
docker: server
	docker build ./server -t pqlserver

.PHONY: client
client: builddir
	gox -osarch="linux/amd64" -output "build/{{.Dir}}" ./...
	GOBIN=$(CURDIR)/build go get github.com/junegunn/fzf
	cd build && fpm -s dir -t deb -n pql -v ${rel} . ./pql=/usr/bin/ ./fzf=/usr/bin/ && cd -
	tar cvf build/pql-${rel}.tar build/fzf build/pql

.PHONY: install
install:
	cd pql && go install && cd -

.PHONY: clean
clean: builddir
	rm -rf build/*

.PHONY: builddir
builddir:
	mkdir -p buildir
