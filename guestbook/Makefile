MAKEFLAGS = --no-print-directory --always-make --silent
MAKE = make $(MAKEFLAGS)

clean:
	@echo "Removing compiled files..."
	lein clean

repl:
	@echo "Connecting to shadow repl..."
	lein repl :connect 7022

clj-server:
	@echo "Starting clj server..."
	lein run

cljs-server:
	@echo "Starting shadow-cljs server..."
	lein shadow watch app

db:
	@echo "Connecting to dev db..."
	psql -U guestbook -d guestbook

watch-test:
	@echo "Watching tests..."
	lein test-refresh
