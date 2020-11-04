(ns ^:dev/once guestbook.app
  "Client-side initialization logic in dev"
  (:require
    [devtools.core :as devtools]

    [guestbook.core :as core]))

(enable-console-print!)

(println "loading env/dev/cljs/guestbook/app.cljs...")

(devtools/install!)

(core/init!)
