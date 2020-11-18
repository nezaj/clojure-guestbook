(ns guestbook.db.core
  (:require
   [clojure.java.jdbc :as jdbc]
   [cheshire.core :refer [generate-string parse-string]]
   [next.jdbc.sql :as sql]
   [next.jdbc.result-set :as rs]
   [conman.core :as conman]
   [java-time.pre-java8 :as jt]
   [java-time :refer [java-date]]
   [mount.core :refer [defstate]]

   [guestbook.config :refer [env]])
  (:import (org.postgresql.util PGobject)))

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
(defn save-message! [{:keys [name message]}]
  (sql/insert! *db* :posts {:name name :message message}))

(defn get-messages []
  (sql/query *db* ["SELECT id, name, message, timestamp FROM posts"] {:builder-fn rs/as-unqualified-maps}))

; Seed data from previous db
; Note: using jdbc here as opposed to next.jdbc so format of datasource is different
; ---------
(comment
  (->>
   (jdbc/query
    {:connection-uri "jdbc:h2:./guestbook_dev.db"}
    ["select name, message, timestamp from guestbook"])
   (jdbc/insert-multi! {:connection-uri (str "jdbc:" (env :database-url))} :posts)))
