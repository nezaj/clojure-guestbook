(ns ring-app.core
  (:require
   [reitit.ring :as reitit]
   [ring.adapter.jetty :as jetty]
   [ring.util.http-response :as response]
   [ring.middleware.reload :refer [wrap-reload]]
   [muuntaja.middleware :as muuntaja]))

(defn wrap-nocache [handler]
  (fn [request]
    (-> request
        handler
        (assoc-in [:headers "Pragma"] "no-cache"))))

(defn wrap-formats [handler]
  (-> handler
      (muuntaja/wrap-format)))

(defn response-handler [request-map]
  (response/ok
   (str "<html><body> your IP is:"
        (:remote-addr request-map)
        "</body></html>")))

(def routes
  [["/" {:get response-handler}]
   ["/echo/:id"
    {:get
     (fn [{{:keys [id]} :path-params}]
       (response/ok (str "<p>the value is: " id "</p>")))}]
   ["/api" {:middleware [wrap-formats]}
    ["/multiply"
     {:post
      (fn [{{:keys [a b]} :body-params}]
        (response/ok {:result (* a b)}))}]]])

(def handler
  (reitit/ring-handler
   (reitit/router routes)))

(defn -main []
  (jetty/run-jetty
   (-> #'handler
       wrap-nocache
       wrap-reload)
   {:port 3000
    :join? false}))
