(ns guestbook.core
  (:require [reagent.core :as r]
            [reagent.dom :as dom]
            [re-frame.core :as rf]
            [mount.core :as mount]
            [ajax.core :refer [GET POST]]
            [clojure.string :as string]
            [reitit.coercion.spec :as reitit-spec]
            [reitit.frontend :as rtf]
            [reitit.frontend.easy :as rtfe]

            [guestbook.validation :refer [validate-message]]
            [guestbook.websockets :as ws]))

;; constants
;; ------------------------
(def SEND_CB_TIMEOUT 10000)

;; effect handlers
;; ------------------------
(rf/reg-fx
 :ajax/get
 (fn [{:keys [url success-event error-event success-path]}]
   (GET url
     (cond-> {:headers {"Accept" "application/transit+json"}}
       success-event (assoc :handler
                            #(rf/dispatch
                              (conj success-event
                                    (if success-path
                                      (get-in % success-path)
                                      %))))
       error-event (assoc :error-handler
                          #(rf/dispatch
                            (conj error-event %)))))))

;; sessions
;; ------------------------
(rf/reg-event-fx
 :session/load
 (fn [{:keys [db]} _]
   {:db (assoc db :session/loading? true)
    :ajax/get {:url "/api/session"
               :success-path [:session]
               :success-event [:session/set]}}))

(rf/reg-event-db
 :session/set
 (fn [db [_ {:keys [identity]}]]
   (assoc db
          :auth/user identity
          :session/loading? false)))

(rf/reg-sub
 :session/loading?
 (fn [db _]
   (:session/loading? db)))

;; message queries
;; ------------------------
(rf/reg-sub
 :messages/loading?
 (fn [db _]
   (:messages/loading? db)))

(rf/reg-sub
 :messages/list
 (fn [db _]
   (:messages/list db [])))

;; form queries
;; ------------------------
(rf/reg-sub
 :form/fields
 (fn [db _]
   (:form/fields db)))

(rf/reg-sub
 :form/field
 :<- [:form/fields]
 (fn [fields [_ id]]
   (get fields id)))

(rf/reg-sub
 :form/validation-errors
 :<- [:form/fields]
 (fn [fields _]
   (validate-message fields)))

(rf/reg-sub
 :form/validation-errors?
 :<- [:form/validation-errors]
 (fn [errors _]
   (not (empty? errors))))

(rf/reg-sub
 :form/errors
 :<- [:form/validation-errors]
 :<- [:form/server-errors]
 (fn [[validation server] _]
   (merge validation server)))

(rf/reg-sub
 :form/error
 :<- [:form/errors]
 (fn [errors [_ id]]
   (get errors id)))

;; message handlers
;; ------------------------
(rf/reg-event-fx
 :messages/load
 (fn [{:keys [db]} _]
   {:db (assoc db :messages/loading? true)
    :ajax/get {:url "/api/messages"
               :success-path [:messages]
               :success-event [:messages/set]}}))

(rf/reg-event-db
 :messages/set
 (fn [db [_ messages]]
   (-> db
       (assoc :messages/loading? false
              :messages/list messages))))

(rf/reg-event-db
 :message/add
 (fn [db [_ message]]
   (.log js/console (str "Adding message " message))
   (update db :messages/list conj message)))

(rf/reg-event-fx
 :message/send!-called-back
 (fn [_ [_ {:keys [success errors]}]]
   (.log js/console
         "Called-back from server! with success: " success
         " error: " errors)
   (if success
     {:dispatch [:form/clear-fields]}
     {:dispatch [:form/set-server-errors errors]})))

(rf/reg-event-fx
 :message/send!
 (fn [{:keys [db]} [_ fields]]
   (.log js/console (str "Sending message with fields " fields))
   {:db (dissoc db :form/server-errors)
    :ws/send! {:message [:message/create! fields]
               :timeout SEND_CB_TIMEOUT
               :callback-event [:message/send!-called-back]}}))

;; form handlers
;; ------------------------

(rf/reg-event-db
 :form/set-field
 [(rf/path :form/fields)]
 (fn [fields [_ id value]]
   (assoc fields id value)))

(rf/reg-event-db
 :form/clear-fields
 [(rf/path :form/fields)]
 (fn [_ _]
   {}))

(rf/reg-event-db
 :form/set-server-errors
 [(rf/path :form/server-errors)]
 (fn [_ [_ errors]]
   errors))

(rf/reg-sub
 :form/server-errors
 (fn [db _]
   (:form/server-errors db)))

;; Modals
;; -------------------


(rf/reg-event-db
 :app/show-modal
 (fn [db [_ modal-id]]
   (assoc-in db [:app/active-modals modal-id] true)))

(rf/reg-event-db
 :app/hide-modal
 (fn [db [_ modal-id]]
   (update db :app/active-modals dissoc modal-id)))

(rf/reg-sub
 :app/active-modals
 (fn [db _]
   (:app/active-modals db {})))

(rf/reg-sub
 :app/modal-showing?
 :<- [:app/active-modals]
 (fn [modals [_ modal-id]]
   (get modals modal-id false)))

(defn modal-card [id title body footer]
  [:div.modal
   {:class (when @(rf/subscribe [:app/modal-showing? id]) "is-active")}
   [:div.modal-background
    {:on-click #(rf/dispatch [:app/hide-modal id])}]
   [:div.modal-card
    [:header.modal-card-head
     [:p.modal-card-title title]
     [:button.delete
      {:on-click #(rf/dispatch [:app/hide-modal id])}]]
    [:section.modal-card-body body]
    [:footer.modal-card-foot footer]]])

(defn modal-button [id title body footer]
  [:div
   [:button.button.is-primary
    {:on-click #(rf/dispatch [:app/show-modal id])}
    title]
   [modal-card id title body footer]])

;; auth
;; ------------------------
(rf/reg-event-db
 :auth/handle-login
 (fn [db [_ {:keys [identity]}]]
   (assoc db :auth/user identity)))

(rf/reg-event-db
 :auth/handle-logout
 (fn [db _]
   (rf/dispatch [:form/clear-fields])
   (dissoc db :auth/user)))

(rf/reg-sub
 :auth/user
 (fn [db _]
   (:auth/user db)))

(rf/reg-sub
 :auth/user-state
 :<- [:auth/user]
 :<- [:session/loading?]
 (fn [[user loading?]]
   (cond
     (true? loading?)  :loading
     user              :authenticated
     :else             :anonymous)))

(defn do-login [fields error]
  (reset! error nil)
  (POST "/api/login"
    {:headers {"Accept" "application/transit+json"}
     :params @fields
     :handler (fn [response]
                (reset! fields {})
                (rf/dispatch [:auth/handle-login response])
                (rf/dispatch [:app/hide-modal :user/login]))
     :error-handler (fn [error-response]
                      (reset! error
                              (or (:message (:response error-response))
                                  (:status-text error-response)
                                  "Unknown Error")))}))

(defn login-button []
  (r/with-let [fields (r/atom {})
               error (r/atom nil)]
    [modal-button :user/login
     ;; Title
     "Log in"
     ;; Body
     [:div
      (when-not (string/blank? @error)
        [:div.notification.is-danger @error])
      [:div.field
       [:div.label "Login"]
       [:div.control
        [:input.input
         {:type "text"
          :value (:login @fields)
          :on-change #(swap! fields assoc :login (.. % -target -value))}]]]
      [:div.field
       [:div.label "Password"]
       [:div.control
        [:input.input
         {:type "password"
          :value (:password @fields)
          :on-change #(swap! fields assoc :password (.. % -target -value))
          ;; Submit login form when 'Enter' key is pressed
          :on-key-down #(when (= (.-keyCode %) 13)
                          (do-login fields error))}]]]]
     ;; Footer
     [:button.button.is-primary.is-fullwidth
      {:on-click #(do-login fields error)
       :disabled (or (string/blank? (:login @fields)) (string/blank? (:password @fields)))}
      "Log In"]]))

(defn logout-button []
  [:button.button
   {:on-click #(POST "/api/logout"
                 :handler (fn [_] (rf/dispatch [:auth/handle-logout])))}
   "Log Out"])

(defn nameplate [{:keys [login]}]
  [:button.button.is-primary
   login])

;; register
;; ------------------------
(defn do-register [fields error]
  (reset! error nil)
  (POST "/api/register"
    {:header {"Accept" "application/transit+json"}
     :params @fields
     :handler (fn [response]
                (reset! fields {})
                (rf/dispatch [:auth/handle-login response])
                (rf/dispatch [:app/hide-modal :user/register]))
     :error-handler (fn [error-response]
                      (reset! error
                              (or (:message (:response error-response))
                                  (:status-text error-response)
                                  "Unknown Error")))}))

(defn register-button []
  (r/with-let
    [fields (r/atom {})
     error (r/atom nil)]
    [modal-button :user/register
     ;; Title
     "Create Account"
     ;; Body
     [:div
      (when-not (string/blank? @error)
        [:div.notification.is-danger
         @error])
      [:div.field
       [:div.label "Login"]
       [:div.control
        [:input.input
         {:type "text"
          :value (:login @fields)
          :on-change #(swap! fields assoc :login (.. % -target -value))}]]]
      [:div.field
       [:div.label "Password"]
       [:div.control
        [:input.input
         {:type "password"
          :value (:password @fields)
          :on-change #(swap! fields assoc :password (.. % -target -value))}]]]
      [:div.field
       [:div.label "Confirm Password"]
       [:div.control
        [:input.input
         {:type "password"
          :value (:confirm @fields)
          :on-change #(swap! fields assoc :confirm (.. % -target -value))
          :on-key-down #(when (= (.-keyCode %) 13)
                          (do-register fields error))}]]]]
     [:button.button.is-primary.is-fullwidth
      {:on-click #(do-register fields error)
       :disabled (some string/blank? [(:login @fields)
                                      (:password @fields)
                                      (:confirm @fields)])}
      "Create Account"]]))

;; actions
;; ------------------------
(defn handle-response! [response]
  (if-let [errors (:errors response)]
    (rf/dispatch [:form/set-server-errors errors])
    (rf/dispatch [:message/add response])))

;; components
;; ------------------------
(defn reload-messages-button []
  (let [loading? (rf/subscribe [:messages/loading?])]
    [:button.button.is-info.is-fullwidth
     {:on-click #(rf/dispatch [:messages/load])
      :disabled @loading?}
     (if @loading? "Loading messages..." "Refresh messages")]))

(defn form-errors-component
  "Form level errors"
  [id & [message]]
  (when-let [error @(rf/subscribe [:form/error id])]
    [:div.notification.is-danger (if message
                                   message
                                   (string/join error))]))
(defn field-errors-component
  "Field level errors"
  [id & [message]]
  (if-let [_ @(rf/subscribe [:form/field id])]
    (when-let [error @(rf/subscribe [:form/error id])]
      [:div.notification.is-danger (if message
                                     message
                                     (string/join error))])))

(defn text-input [{val :value
                   attrs :attrs
                   :keys [on-save]}]
  (let [draft (r/atom nil)
        value (r/track #(or @draft @val ""))]
    (fn []
      [:input.input
       (merge attrs
              {:type :text
               :on-focus #(reset! draft (or @val ""))
               :on-blur (fn []
                          (on-save (or @draft ""))
                          (reset! draft nil))
               :on-change #(reset! draft (.. % -target -value))
               :value @value})])))

(defn textarea-input [{val :value
                       attrs :attrs
                       :keys [on-save]}]
  (let [draft (r/atom nil)
        value (r/track #(or @draft @val ""))]
    (fn []
      [:textarea.textarea
       (merge attrs
              {:type :text
               :on-focus #(reset! draft (or @val ""))
               :on-blur (fn []
                          (on-save (or @draft ""))
                          (reset! draft nil))
               :on-change #(reset! draft (.. % -target -value))
               :value @value})])))

(defn message-form []
  [:div
   [form-errors-component :server-error]
   [form-errors-component :unauthorized]
   [:div.field
    [:label.label {:for :name} "Name"]
    [field-errors-component :name]
    [text-input {:attrs {:name :name}
                 :value (rf/subscribe [:form/field :name])
                 :on-save #(rf/dispatch [:form/set-field :name %])}]]
   [:div.field
    [:label.label {:for :message} "Message"]
    [field-errors-component :message]
    [textarea-input
     {:attrs {:name :message}
      :value (rf/subscribe [:form/field :message])
      :on-save #(rf/dispatch [:form/set-field :message %])}]]
   [:input.button.is-primary
    {:type :submit
     :disabled @(rf/subscribe [:form/validation-errors?])
     :on-click #(rf/dispatch [:message/send!
                              @(rf/subscribe [:form/fields])])
     :value "comment"}]])

(defn message-list [messages]
  [:ul.messages
   (for [{:keys [timestamp message name author]} @messages]
     ^{:key timestamp}
     [:li
      [:time (.toLocaleString timestamp)]
      [:p message]
      [:p "-" name
       " <"
       (if author
         [:a {:href (str "/user/" author)} (str "@" author)]
         [:span.is-italic "account not found"]) ">"]])])

(defn navbar []
  (let [burger-active (r/atom false)]
    (fn []
      [:nav.navbar.is-info
       [:div.container
        [:div.navbar-brand
         [:a.navbar-item
          {:href "/"
           :style {:font-weight "bold"}}
          "guestbook"]
         [:span.navbar-burger.burger
          {:data-target "nav-menu"
           :on-click #(swap! burger-active not)
           :class (when @burger-active "is-active")}
          [:span]
          [:span]
          [:span]]]
        [:div#nav-menu.navbar-menu
         {:class (when @burger-active "is-active")}
         [:div.navbar-start
          [:a.navbar-item
           {:href "/"}
           "Home"]]
         [:div.navbar-end
          [:div.navbar-item
           (case @(rf/subscribe [:auth/user-state])
             :loading
             [:div {:style {:width "5em"}}
              [:progress.progress.is-dark.is-small {:max 100} "30%"]]

             :authenticated
             [:div.buttons
              [nameplate @(rf/subscribe [:auth/user])]
              [logout-button]]

             :anonymous
             [:div.buttons
              [login-button]
              [register-button]])]]]]])))

(defn home []
  (let [messages (rf/subscribe [:messages/list])]
    (fn []
      [:div.content>div.columns.is-centered>div.column.is-two-thirds
       (if @(rf/subscribe [:messages/loading?])
         [:h3 "Loading Messages..."]
         [:div
          [:div.columns>div.column
           [:h3 "Messages"]
           [message-list messages]
           [reload-messages-button]]
          [:div.columns>div.column
           (case @(rf/subscribe [:auth/user-state])
             :loading
             [:div {:style {:width "5em"}}
              [:progress.progress.is-dark.is-small {:max 100} "30%"]]

             :authenticated
             [message-form]

             :anonymous
             [:div.notification.is-clearfix
              [:span "Log in or create an account to post a message!"]
              [:div.buttons.is-pulled-right
               [login-button]
               [register-button]]])]])])))

(defn author []
  [:div
   [:p "This page hasn't been implemented yet!"]
   [:a {:href "/"} "Return home"]])

;; router
;; ------------------------
(def routes
  ["/"
   [""
    {:name ::home
     :view home}]
   ["user/:user"
    {:name ::author
     :view author}]])

(rf/reg-event-db
 :router/navigated
 (fn [db [_ new-match]]
   (assoc db :router/current-route new-match)))

(rf/reg-sub
 :router/current-route
 (fn [db]
   (:router/current-route db)))

(def router
  (rtf/router
   routes
   {:data {:coercion reitit-spec/coercion}}))

(defn init-routes! []
  (rtfe/start!
   router
   (fn [new-match]
     (when new-match
       (rf/dispatch [:router/navigated new-match])))
   {:use-fragment false}))

;; app init
;; ------------------------
(defn page [{{:keys [view name]}  :data
             path                 :path}]
  [:section.section>div.container
   (if view
     [view]
     [:div "No views specified for route: " name " (" path ")"])])

(defn app []
  (let [current-route @(rf/subscribe [:router/current-route])]
    [:div.app
     [navbar]
     [page current-route]]))

(defn ^:dev/after-load mount-components []
  (rf/clear-subscription-cache!)
  (.log js/console "Mounting components...")
  (init-routes!)
  (dom/render [#'app] (.getElementById js/document "content"))
  (.log js/console "Components Mounted!"))

(rf/reg-event-fx
 :app/initialize
 (fn [_ _]
   {:db {:messages/loading? true
         :session/loading? true}
    :dispatch-n [[:session/load] [:messages/load]]}))

(defn init! []
  (.log js/console "Initializing App...")
  (mount/start)
  (rf/dispatch [:app/initialize])
  (mount-components))
