MAKEFLAGS = --no-print-directory --always-make --silent
MAKE = make $(MAKEFLAGS)

repl:
	@echo "Starting repl..."
	lein repl

clean:
	@echo "Removing compiled files..."
	lein clean

dev-server:
	@echo "Starting dev server..."
	lein run

dev-client:
	@echo "Starting client server..."
	lein cljsbuild auto

watch-test:
	@echo "Watching tests..."
	lein test-refresh
