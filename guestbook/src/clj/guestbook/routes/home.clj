(ns guestbook.routes.home
  (:require
   [ring.util.response]

   [guestbook.layout :as layout]
   [guestbook.middleware :as middleware]))

(defn home-page [request]
  (layout/render request "home.html"))

(defn home-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get home-page}]
   ["/index.html" {:get home-page}]])
