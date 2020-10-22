MAKEFLAGS = --no-print-directory --always-make --silent
MAKE = make $(MAKEFLAGS)

repl:
	@echo "Starting repl..."
	lein repl

watch-test:
	@echo "Watching tests..."
	lein test-refresh
