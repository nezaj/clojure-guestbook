(ns guestbook.routes.websockets
  (:require [clojure.tools.logging :as log]

            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
            [mount.core :refer [defstate]]
            [taoensso.sente :as sente]

            [guestbook.auth :as auth]
            [guestbook.session :as session]
            [guestbook.messages :as msg]
            [guestbook.middleware :as middleware]))

(defstate socket
  :start (sente/make-channel-socket!
          (get-sch-adapter)
          {:user-id-fn (fn [ring-req]
                         (get-in ring-req [:params :client-id]))}))

(defn send! [uid message]
  (log/info "Sending message: " message)
  ((:send-fn socket) uid message))

(defmulti handle-message (fn [{:keys [id]}]
                           id))

(defmethod handle-message :default
  [{:keys [id]}]
  (log/info "Received unrecognized websocket event type: " id)
  {:error (str "Unrecognized websocket event type: " (pr-str id))
   :id id})

(defmethod handle-message :message/create!
  [{:keys [?data session]}]
  (let [response (try
                   (msg/save-message! (:identity session) ?data)
                   (assoc ?data
                          :timestamp (java.util.Date.)
                          :author (get-in session [:identity :login]))
                   (catch Exception e
                     (let [{id :guestbook/error-id
                            errors :errors} (ex-data e)]
                       (case id
                         :validation
                         {:errors errors}
                         {:errors {:server-error ["Failed to save message!"]}}))))]
    (if (:errors response)
      (do
        (log/info "Failed to save message: " ?data)
        response)
      (do
        (doseq [uid (:any @(:connected-uids socket))]
          (send! uid [:message/add response]))
        {:success true}))))

(defn receive-message! [{:keys [id ?reply-fn ring-req]
                         :as message}]
  (case id
    :chsk/bad-package   (log/debug "Bad Package:\n" message)
    :chsk/bad-event     (log/debug "Bad Event: \n" message)
    :chsk/uidport-open  (log/trace (:event message))
    :chsk/uidport-close (log/trace (:event message))
    :chsk/ws-ping       nil
    ;; else
    (let [reply-fn (or ?reply-fn (fn [_]))
          session (session/read-session ring-req)
          message (-> message
                      (assoc :session session))]
      (log/info "Got message with id: " id)
      (if (auth/ws-authorized? auth/roles message)
        (when-some [response (handle-message message)]
          (reply-fn response))
        (do
          (log/info "Unauthorized message: " id)
          (reply-fn {:message "You are not authorized to perform this action!"
                     :errors {:unauthorized true}}))))))

(defstate channel-router
  :start (sente/start-chsk-router!
          (:ch-recv socket)
          #'receive-message!)
  :stop (when-let [stop-fn channel-router]
          (stop-fn)))

(defn websocket-routes []
  ["/ws"
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]
    :get (:ajax-get-or-ws-handshake-fn socket)
    :post (:ajax-post-fn socket)}])
