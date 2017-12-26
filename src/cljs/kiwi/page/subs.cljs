(ns kiwi.page.subs
  (:require
   [re-frame.core :as re-frame :refer [reg-sub]]))

(reg-sub
 :current-page
 (fn [db _]
   (get-in db [:route-state :page])))

(reg-sub
 :permalinks
 (fn [db _]
   (get-in db [:route-state :permalinks])))
