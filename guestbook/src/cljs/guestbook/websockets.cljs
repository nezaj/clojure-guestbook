(ns guestbook.websockets
  (:require [cljs.reader :as edn]
            [re-frame.core :as rf]))

(defonce channel (atom nil))

(defn connect! [url receive-handler]
  (if-let [chan (js/WebSocket. url)]
    (do
      (.log js/console "Connected!")
      (set! (.-onmessage chan) #(->> %
                                     .-data
                                     edn/read-string
                                     receive-handler))
      (reset! channel chan))
    (throw (ex-info "Websocket connection failed!"
                    {:url url}))))

(defn send-message! [msg]
  (if-let [chan @channel]
    (do
      (.send chan (pr-str msg))
      (rf/dispatch [:form/clear-fields]))
    (throw (ex-info "Failed to send message, channel isn't open!"
                    {:message msg}))))
