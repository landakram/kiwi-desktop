(ns kiwi.setup.subs
  (:require [re-frame.core :as re-frame :refer [reg-sub]]))

(reg-sub
 :setup-route
 (fn [db _]
   (get-in db [:setup-state :route])))

