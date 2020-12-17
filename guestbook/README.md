# Guestbook

Guestbook project from Web Development With Clojure

### Quick-start

**Note:** Assumes `postgres` is already installed and running on port `5432` and that `psql` is available on the command line

```
# Create postgres database from terminal
psql
CREATE user guestbook WITH PASSWORD 'guestbook';
CREATE DATABASE guestbook OWNER guestbook;
\q

# Set up dev config
cp sample-config.edn dev-config.edn

# Start up shadow server
make dev

# In another terminal instance connect to repl
make repl

# In repl start up app
(guestbook.core/-main)
```

The app should now be running at `localhost:3020`

### REPLs

To hop into cljs repl from clj repl

```
(in-ns 'shadow.user)
(shadow/repl :app)
```

To go back to clj repl from cljs repl

```
:cljs/quit
```

You can also be connected to both clj and cljs repl at the same time by via two terminal instances.

```
# Connect to clj repl in one terminal instance
make repl

# Connect to cljs repl in another terminal instance
make repl
(shadow/repl :app)
```

### Vim Quasi-REPL (via vim-fireplace)

Vim should automagically connect to your running clj repl. To connect vim to a cljs repl: open up your app on localhost with shadow running, execute in vim

```
:Piggieback :app
```

You should now be able to use the quasi-repl in both clj and cljs files
