(ns guestbook.author
  (:require [guestbook.db.core :as db]))

(defn get-author [login]
  (db/get-user {:login login}))


