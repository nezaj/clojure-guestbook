MAKEFLAGS = --no-print-directory --always-make --silent
MAKE = make $(MAKEFLAGS)

dev:
	@echo "Spinning up dev..."
	lein shadow watch app

repl:
	@echo "Connecting to shadow repl..."
	lein repl :connect 7022

db:
	@echo "Connecting to dev db..."
	psql -U guestbook -d guestbook

clean:
	@echo "Removing compiled files..."
	lein clean

watch-test:
	@echo "Watching tests..."
	lein test-refresh
