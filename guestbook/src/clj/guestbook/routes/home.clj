(ns guestbook.routes.home
  (:require
   [guestbook.layout :as layout]
   [guestbook.db.core :as db]
   [clojure.java.io :as io]
   [guestbook.middleware :as middleware]
   [ring.util.response]
   [ring.util.http-response :as response]
   [struct.core :as st]
   [guestbook.validation :refer [validate-message]]))

(defn home-page [request]
  (layout/render request "home.html"))

(defn about-page [request]
  (layout/render request "about.html"))

(defn save-message! [{:keys [params]}]
  (if-let [errors (validate-message params)]
    (do
      (println System/out "Oh noes...")
      (response/bad-request {:errors errors}))
    (try
      (db/save-message! params)
      (println System/out "Saved message!")
      (response/ok {:status :ok})
      (catch Exception _
        (response/internal-server-error
         {:errors {:server-error ["Failed to save message!"]}})))))

(defn message-list [_]
  (response/ok {:messages (vec (db/get-messages))}))

(defn home-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get home-page}]
   ["/index.html" {:get home-page}]
   ["/about" {:get about-page}]
   ["/messages" {:get message-list}]
   ["/message" {:post save-message!}]])
