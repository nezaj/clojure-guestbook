# Guestbook

Guestbook project from Web Development With Clojure

### REPLs

**Clojure**: `make clj-repl` will fire-up an nrepl where you can evaluate code and hook-up your editor for navigation. If using vim and vim-fireplace, you should be automagically connected to the clojure repl once you open a `.clj` file

**ClojureScript**: This is a bit more involved and requires the following:

-   `make dev-client` will spin-up a cljs-build tool to build/watch your cljs files
-   `open localhost:3000` will open up a web-socket connection between your browser and your cljs server
-   `make cljs-repl` to start a cljs nrepl which will use the browser to evaluate cljs code
-   Open vim and enter the command `:Piggieback :app` to connect vim to your cljs repl.

###### Note: Currently I can only get `vim-fireplace` to reliabily connect to one repl at a time. If I'm first connected to a clojure repl I will want to quit that the repl process and start a new cljs repl via `make cljs-repl`. Similarly if I'm connected to a cljs-repl and want to switch to a clj-repl I will want to exit and run `make clj-repl`.
