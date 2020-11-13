(ns reporting-example.db.seed
  (:require
   [next.jdbc.sql :as sql]
   [mount.core :as mount]

   [reporting-example.config :refer [env]]
   [reporting-example.db.core :refer [*db*] :as db]))

(defn seed []
  (sql/insert-multi!
   *db*
   :employee
   [:name :occupation :place :country]
   [["Albert Einstein" "Engineer" "Ulm" "Germany"]
    ["Alfred Hitchcock" "Movie Director" "London" "UK"]
    ["Werhnher von Braun" "Rocket Scientist" "Wyrzysk" "Poland"]
    ["Sigmund Freud" "Neurologist" "Pribor" "Czech Republic"]
    ["Mahatma Gandhi" "Laywer" "Gujarat" "India"]
    ["Sachin Tendulkar" "Cricket Player" "Mumbai" "India"]
    ["Michael Schumacher" "F1 Racer" "Cologne" "Germany"]]))

(defn truncate []
  (sql/query *db* ["DELETE FROM employee"]))

(defn start-db []
  (mount/stop #'env #'*db*)
  (mount/start #'env #'*db*))

(defn re-populate []
  (println "Re-populating db...")
  (start-db)
  (truncate)
  (seed)
  (println "Populated!"))
