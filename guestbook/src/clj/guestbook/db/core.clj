(ns guestbook.db.core
  (:require
   [clojure.tools.logging :as log]
   [clojure.java.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [next.jdbc.result-set :as rs]
   [conman.core :as conman]
   [java-time.pre-java8 :as jt]
   [java-time :refer [java-date]]
   [mount.core :refer [defstate]]

   [guestbook.config :refer [env]]))

(defstate ^:dynamic *db*
  :start (conman/connect! {:jdbc-url (env :database-url)})
  :stop (conman/disconnect! *db*))

(extend-protocol jdbc/IResultSetReadColumn
  java.sql.Timestamp
  (result-set-read-column [v _2 _3]
    (java-date (.atZone (.toLocalDateTime v) (java.time.ZoneId/systemDefault))))
  java.sql.Date
  (result-set-read-column [v _2 _3]
    (.toLocalDate v))
  java.sql.Time
  (result-set-read-column [v _2 _3]
    (.toLocalTime v)))
;

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
    (jt/sql-timestamp v)))

; queries
; -------------
(defn db-query [conn sql]
  (log/info "Running query " sql)
  (sql/query conn sql {:builder-fn rs/as-unqualified-maps}))

(defn db-insert! [conn table-key kvs]
  (log/info "Inserting into " table-key kvs)
  (sql/insert! conn table-key kvs {:builder-fn rs/as-unqualified-maps}))

(defn get-messages
  ([] (get-messages *db*))
  ([conn] (db-query conn ["SELECT * FROM posts"])))

(defn get-user-for-auth
  ([params] (get-user-for-auth *db* params))
  ([conn {:keys [login]}]
   (first (db-query conn ["SELECT * FROM users WHERE login = ?" login]))))

(defn get-messages-by-author
  ([params] (get-messages-by-author *db* params))
  ([conn {:keys [author]}]
   (db-query conn ["SELECT * FROM posts where author = ?" author])))

(defn create-user!
  ([params] (create-user! *db* params))
  ([conn params]
   (db-insert! conn
               :users
               (select-keys params [:login :password]))))

(defn save-message!
  ([params] (save-message! *db* params))
  ([conn params]
   (db-insert! conn
               :posts
               (select-keys params [:name :message :author]))))

;; one-off migration of data from h2 to postgres
;; Note: using jdbc here as opposed to next.jdbc so format of datasource is different
;; ---------
(comment
  (->>
   (jdbc/query
    {:connection-uri "jdbc:h2:./guestbook_dev.db"}
    ["select name, message, timestamp from guestbook"])
   (jdbc/insert-multi! {:connection-uri (str "jdbc:" (env :database-url))} :posts)))
