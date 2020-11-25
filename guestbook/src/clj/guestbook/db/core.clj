(ns guestbook.db.core
  (:require
   [clojure.tools.logging :as log]
   [clojure.java.jdbc :as jdbc]
   [cheshire.core :refer [generate-string parse-string]]
   [next.jdbc :as next-jdbc]
   [next.jdbc.sql :as sql]
   [next.jdbc.result-set :as rs]
   [conman.core :as conman]
   [java-time.pre-java8 :as jt]
   [mount.core :refer [defstate]]

   [guestbook.config :refer [env]])
  (:import org.postgresql.util.PGobject
           java.sql.Array
           clojure.lang.IPersistentMap
           clojure.lang.IPersistentVector))

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

(extend-protocol jdbc/ISQLValue
  java.util.Date
  (sql-value [v]
    (java.sql.Timestamp. (.getTime v)))
  java.time.LocalTime
  (sql-value [v]
    (jt/sql-time v))
  java.time.LocalDate
  (sql-value [v]
    (jt/sql-date v))
  java.time.LocalDateTime
  (sql-value [v]
    (jt/sql-timestamp v))
  java.time.ZonedDateTime
  (sql-value [v]
    (jt/sql-timestamp v))
  IPersistentMap
  (sql-value [v]
    (->pgobject v))
  IPersistentVector
  (sql-value [v]
    (->pgobject v)))

; queries
; -------------
(defn db-select [conn query]
  (log/info "Running select: " query)
  (sql/query conn query {:builder-fn rs/as-unqualified-maps}))

(def db-select-one (comp first db-select))

(defn db-insert! [conn query]
  (log/info "Running insert: " query)
  (next-jdbc/execute-one! conn query {:builder-fn rs/as-unqualified-maps
                                      :return-keys true}))

(defn db-update! [conn query]
  (log/info "Running update: " query)
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
   (db-insert! conn
               ["INSERT INTO users (login,password) VALUES (?,?)"
                login password])))

(defn save-message!
  ([params] (save-message! *db* params))
  ([conn {:keys [name message author]}]
   (db-insert! conn
               ["INSERT INTO posts (name,message,author) VALUES (?,?,?)"
                name message author])))

(defn set-profile-for-user!
  ([params] (set-profile-for-user! *db* params))
  ([conn {:keys [profile login]}]
   (db-update! conn ["UPDATE users SET profile = ?::jsonb WHERE login = ?"
                     (->json profile) login])))

;; one-off migration of data from h2 to postgres
;; Note: using jdbc here as opposed to next.jdbc so format of datasource is different
;; ---------
(comment
  (->>
   (jdbc/query
    {:connection-uri "jdbc:h2:./guestbook_dev.db"}
    ["select name, message, timestamp from guestbook"])
   (jdbc/insert-multi! {:connection-uri (str "jdbc:" (env :database-url))} :posts)))
