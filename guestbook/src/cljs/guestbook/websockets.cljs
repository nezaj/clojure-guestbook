(ns guestbook.websockets
  (:require-macros [mount.core :refer [defstate]])
  (:require [re-frame.core :as rf]
            [taoensso.sente :as sente]
            mount.core))
;; constants
;; ------------------------
(def CB_EVENT_TIMEOUT 30000)

(defstate socket
  "Opens a websockets channel between the requesting client and our server"
  :start (sente/make-channel-socket!
          "/ws"
          (.-value (.getElementById js/document "token"))
          {:type :auto
           :wrap-recv-evs? false}))

(defn send!
  "Sends event using receiving socket send-fn"
  [& args]
  (if-let [send-fn (:send-fn @socket)]
    (apply send-fn args)
    (throw (ex-info "Couldn't send message, channel isn't open!"
                    {:message (first args)}))))

(rf/reg-fx
 :ws/send!
 (fn [{:keys [message timeout callback-event]
       :or {timeout CB_EVENT_TIMEOUT}}]
   (if callback-event
     (send! message timeout #(rf/dispatch (conj callback-event %)))
     (send! message))))

(defmulti handle-message
  "After receiving a message over our websocket connection, we dispatch the
  appropriate event based on the event type"
  (fn [{:keys [id]} _]
    id))

;; application-specific message handlers
;; ------------------------
(defmethod handle-message :message/add
  [_ msg-add-event]
  (rf/dispatch msg-add-event))

(defmethod handle-message :message/creation-errors
  [_ [_ response]]
  (rf/dispatch
   [:form/set-server-errors (:errors response)]))

;; Generic message handlers
;; ------------------------
(defmethod handle-message :chsk/handshake
  [{:keys [event]} _]
  (.log js/console "Connection Established: " (pr-str event)))

(defmethod handle-message :chsk/state
  [{:keys [event]} _]
  (.log js/console "State Changed: " (pr-str event)))

(defmethod handle-message :default
  [{:keys [event]} _]
  (.warn js/console "Unknown websocket message: " (pr-str event)))

;; Websocket routing
;; ------------------------
(defn receive-message!
  "Helper for routing recieved messages to the appropriate message handler"
  [{:keys [id event] :as ws-message}]
  (.log js/console "Event received: " (pr-str id))
  (handle-message ws-message event))

(defstate channel-router
  "Mounts a sente router for accepting messages"
  :start (sente/start-chsk-router!
          (:ch-recv @socket)
          #'receive-message!)
  :stop (when-let [stop-fn @channel-router]
          (stop-fn)))
