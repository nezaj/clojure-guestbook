(ns guestbook.app
  (:require
   [guestbook.core :as core]))

;; no-op println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
