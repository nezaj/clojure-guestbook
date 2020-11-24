(ns guestbook.routes.services
  (:require
   [clojure.tools.logging :as log]
   [spec-tools.data-spec :as ds]

   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]
   [reitit.ring.coercion :as coercion]
   [reitit.coercion.spec :as spec-coercion]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.exception :as exception]
   [reitit.ring.middleware.multipart :as multipart]
   [reitit.ring.middleware.parameters :as parameters]
   [ring.util.http-response :as response]

   [guestbook.auth :as auth]
   [guestbook.messages :as msg]
   [guestbook.middleware.formats :as formats]))

(defn unauthorized-handler [req]
  (let [route-roles (auth/get-roles-from-match req)]
    (log/info "Roles for route: " (:uri req) route-roles)
    (log/info "User is unauthorized! User roles: "
              (-> req
                  :session
                  :identity
                  :roles))
    (response/forbidden
     {:message
      (str "User must have one of the following roles: "
           route-roles)})))

(defn service-routes []
  ["/api"
   {:middleware [;; query-params & form-params
                 parameters/parameters-middleware
                 ;; content-negotiation
                 muuntaja/format-negotiate-middleware
                 ;; encoding response body
                 muuntaja/format-response-middleware
                 ;; exception handling
                 exception/exception-middleware
                 ;; decoding request body
                 muuntaja/format-request-middleware
                 ;; coercing response bodys
                 coercion/coerce-response-middleware
                 ;; coercing request parameters
                 coercion/coerce-request-middleware
                 ;; multipart params
                 multipart/multipart-middleware
                 ;; Authorization
                 (fn [handler]
                   (auth/wrap-authorized
                    handler
                    unauthorized-handler))]
    :muuntaja formats/instance
    :coercion spec-coercion/coercion
    :swagger {:id ::api}}
   ["" {:no-doc true}
    ["/swagger.json"
     {:auth/roles (auth/roles :swagger/swagger)
      :get (swagger/create-swagger-handler)}]
    ["/swagger-ui*"
     {:auth/roles (auth/roles :swagger/swagger)
      :get (swagger-ui/create-swagger-ui-handler
            {:url "/api/swagger.json"})}]]
   ["/session"
    {:auth/roles (auth/roles :session/get)
     :get
     {:responses
      {200
       {:body
        {:session
         {:identity
          (ds/maybe
           {:login string?
            :created_at inst?})}}}}
      :handler
      (fn [{{:keys [identity]} :session}]
        (response/ok {:session
                      {:identity
                       (not-empty
                        (select-keys identity [:login :created_at]))}}))}}]
   ["/login"
    {:auth/roles (auth/roles :auth/login)
     :post {:parameters
            {:body
             {:login string?
              :password string?}}
            :responses
            {200
             {:body
              {:identity
               {:login string?
                :created_at inst?}}}
             401
             {:body
              {:message string?}}}
            :handler
            (fn [{{{:keys [login password]} :body}  :parameters
                  session                           :session}]
              (if-some [user (auth/authenticate-user login password)]
                (->
                 (response/ok
                  {:identity user})
                 (assoc :session (assoc session
                                        :identity
                                        user)))
                (response/unauthorized
                 {:message "Incorrect login or password."})))}}]
   ["/logout"
    {:auth/roles (auth/roles :auth/logout)
     :post
     {:handler (fn [req]
                 (log/info "Logging out!")
                 (->
                  (response/ok)
                  (assoc :session
                         (select-keys
                          (:session req)
                          [:ring.middleware.anti-forgery/anti-forgery-token]))))}}]
   ["/register"
    {:auth/roles (auth/roles :account/register)
     :post
     {:parameters
      {:body
       {:login string?
        :password string?
        :confirm string?}}
      :responses
      {200
       {:body
        {:identity
         {:login string?
          :created_at inst?}}}
       400 {:body {:message string?}}
       409 {:body {:message string?}}}
      :handler
      (fn [{{{:keys [login password confirm]} :body} :parameters
            session :session}]
        (if-not (= password confirm)
          (response/bad-request
           {:message "Passwords do not match"})
          (try
            (auth/register-user! login password)
            (let [user (auth/authenticate-user login password)]
              (->
               (response/ok
                {:identity user})
               (assoc :session (assoc session
                                      :identity
                                      user))))
            (catch clojure.lang.ExceptionInfo e
              (if (= (:guestbook/error-id (ex-data e))
                     ::auth/duplicate-user)
                (response/conflict
                 {:message "A user with this login already exists"})
                (throw e))))))}}]
   ["/messages"
    {:auth/roles (auth/roles :messages/list)}
    ["" {:get
         {:responses
          {200
           {:body
            {:messages
             [{:id pos-int?
               :name string?
               :message string?
               :timestamp inst?}]}}}
          :handler
          (fn [_]
            (response/ok (msg/message-list)))}}]
    ["/by/:author"
     {:get
      {:parameters {:path {:author string?}}
       :responses
       {200
        {:body
         {:messages
          [{:id pos-int?
            :name string?
            :message string?
            :timestamp inst?}]}}}
       :handler
       (fn [{{{:keys [author]} :path} :parameters}]
         (response/ok (msg/messages-by-author author)))}}]]
   ["/message"
    {:auth/roles (auth/roles :message/create!)
     :post
     {:parameters
      {:body
       {:name string?
        :message string?}}

      :responses
      {200
       {:body map?}}

      :handler
      (fn [{{params :body} :parameters
            {:keys [identity]} :session}]
        (try
          (->> (msg/save-message! identity params)
               (assoc {:status :ok} :post)
               (response/ok))
          (catch Exception e
            (let [{id :guestbook/error-id
                   errors :errors} (ex-data e)]
              (case id
                :validation
                (response/bad-request {:errors errors})

                :else
                (response/internal-server-error
                 {:errors {:server-error ["Failed to save message!"]}}))))))}}]])
