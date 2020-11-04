MAKEFLAGS = --no-print-directory --always-make --silent
MAKE = make $(MAKEFLAGS)

clj-repl:
	@echo "Starting clj repl..."
	lein repl

cljs-repl:
	@echo "Starting cljs repl..."
	npx shadow-cljs cljs-repl app

clean:
	@echo "Removing compiled files..."
	lein clean

dev-server:
	@echo "Starting dev server..."
	lein run

dev-client:
	@echo "Starting client server..."
	npx shadow-cljs watch app

watch-test:
	@echo "Watching tests..."
	lein test-refresh
