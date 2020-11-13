(ns reporting-example.routes.home
  (:require
   [clojure.java.io :as io]

   [ring.util.response]
   [ring.util.http-response :as response]

   [reporting-example.db.core :as db]
   [reporting-example.layout :as layout]
   [reporting-example.middleware :as middleware]
   [reporting-example.reports :as reports]))

(defn home-page [request]
  (layout/render request "home.html"))

(defn about-page [request]
  (layout/render request "about.html"))

(defn write-response [report-bytes]
  (with-open [in (java.io.ByteArrayInputStream. report-bytes)]
    (-> (response/ok in)
        (response/header "Content-Disposition" "filename=document.pdf")
        (response/header "Content-length" (count report-bytes))
        (response/content-type "application/pdf"))))

(defn generate-report [report-type]
  (try
    (let [out (java.io.ByteArrayOutputStream.)]
      (condp = (keyword report-type)
        :table (reports/table-report out)
        :list (reports/list-report out))
      (write-response (.toByteArray out)))
    (catch Exception ex
      (layout/render "home.html" {:error (.getMessage ex)}))))

(defn home-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get home-page}]
   ["/about" {:get about-page}]
   ["/report/:report-type" {:get (fn [{:keys [path-params]}]
                                   (generate-report (:report-type path-params)))}]])
