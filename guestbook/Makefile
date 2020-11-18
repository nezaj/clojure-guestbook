MAKEFLAGS = --no-print-directory --always-make --silent
MAKE = make $(MAKEFLAGS)

clj-repl:
	@echo "Starting clj repl..."
	lein repl :connect 7000

cljs-repl:
	@echo "Starting cljs repl..."
	npx shadow-cljs cljs-repl app

clean:
	@echo "Removing compiled files..."
	lein clean

clj-server:
	@echo "Starting dev server..."
	lein run

cljs-server:
	@echo "Starting client server..."
	npx shadow-cljs watch app

watch-test:
	@echo "Watching tests..."
	lein test-refresh
