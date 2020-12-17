(ns guestbook.db.core
  (:require
   [clojure.tools.logging :as log]
   [cheshire.core :refer [generate-string parse-string]]
   [next.jdbc :as next-jdbc]
   [next.jdbc.sql :as sql]
   [next.jdbc.prepare :as p]
   [next.jdbc.result-set :as rs]
   [conman.core :as conman]
   [mount.core :refer [defstate]]

   [guestbook.config :refer [env]])
  (:import (org.postgresql.util PGobject)
           (java.sql PreparedStatement)
           (java.time
            Instant
            LocalDate
            LocalDateTime)
           (java.sql Array)
           (clojure.lang IPersistentMap)))

(defstate ^:dynamic *db*
  :start (conman/connect! {:jdbc-url (env :database-url)})
  :stop (conman/disconnect! *db*))

(def ->json generate-string)
(def <-json #(parse-string % true))

(defn ->pgobject
  "Transforms Clojure data to a PGobject that contains the data as
  JSON. PGObject type defaults to `jsonb` but can be changed via
  metadata key `:pgtype`"
  [x]
  (let [pgtype (or (:pgtype (meta x) "jsonb"))]
    (doto (PGobject.)
      (.setType pgtype)
      (.setValue (->json x)))))

(defn <-pgobject
  "Transform PGobject containing `json` or `jsonb` value to Clojure data"
  [^PGobject v]
  (let [type (.getType v)
        value (.getValue v)]
    (if (#{"jsonb" "json"} type)
      (when value
        (with-meta (<-json value) {:pgtype type}))
      value)))

(extend-protocol rs/ReadableColumn
  Array
  (read-column-by-label [^Array v _] (vec (.getArray v)))
  (read-column-by-index [^Array v _2 _3] (vec (.getArray v)))

  PGobject
  (read-column-by-label [^PGobject v _] (<-pgobject v))
  (read-column-by-index [^PGobject v _2 _3] (<-pgobject v)))

(extend-protocol p/SettableParameter
  Instant
  (set-parameter [^java.time.Instant v ^PreparedStatement ps ^long i]
    (.setTimestamp ps i (java.sql.Timestamp/from v)))

  LocalDate
  (set-parameter [^java.time.LocalDate v ^PreparedStatement ps ^long i]
    (.setTimestamp ps i (java.sql.Timestamp/valueOf (.atStartOfDay v))))

  LocalDateTime
  (set-parameter [^java.time.LocalDateTime v ^PreparedStatement ps ^long i]
    (.setTimestamp ps i (java.sql.Timestamp/valueOf v)))

  IPersistentMap
  (set-parameter [m ^PreparedStatement s i]
    (.setObject s i (->pgobject m))))


; queries
; -------------


(defn db-select [conn query]
  (log/info "Running select: " query)
  (sql/query conn query {:builder-fn rs/as-unqualified-maps}))

(def db-select-one (comp first db-select))

(defn db-execute-one! [conn query]
  (log/info "Running query: " query)
  (next-jdbc/execute-one! conn query {:builder-fn rs/as-unqualified-maps
                                      :return-keys true}))
(defn get-messages
  "Grabs all posts"
  ([] (get-messages *db*))
  ([conn] (db-select conn ["SELECT * FROM posts"])))

(defn get-user-for-auth
  "Get all user info, intended for auth"
  ([params] (get-user-for-auth *db* params))
  ([conn {:keys [login]}]
   (db-select-one conn ["SELECT * FROM users WHERE login = ?"
                        login])))

(defn get-messages-by-author
  "Grabs all posts for an author"
  ([params] (get-messages-by-author *db* params))
  ([conn {:keys [author]}]
   (db-select conn ["SELECT * FROM posts where author = ?" author])))

(defn get-user
  "Get public info for a user"
  ([params] (get-user *db* params))
  ([conn {:keys [login]}]
   (db-select-one conn
                  ["SELECT login, created_at, profile FROM users WHERE login = ?"
                   login])))

(defn create-user!
  ([params] (create-user! *db* params))
  ([conn {:keys [login password]}]
   (db-execute-one! conn
                    ["INSERT INTO users (login,password) VALUES (?,?)"
                     login password])))

(defn save-message!
  ([params] (save-message! *db* params))
  ([conn {:keys [name message author]}]
   (db-execute-one! conn
                    ["INSERT INTO posts (name,message,author) VALUES (?,?,?)"
                     name message author])))

(defn set-profile-for-user!
  ([params] (set-profile-for-user! *db* params))
  ([conn {:keys [profile login]}]
   (db-execute-one! conn ["UPDATE users SET profile = ?::jsonb WHERE login = ?"
                          profile login])))
