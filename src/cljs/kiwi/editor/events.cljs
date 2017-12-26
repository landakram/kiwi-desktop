(ns kiwi.editor.events
  (:require [re-frame.core
             :as
             re-frame
             :refer
             [after enrich path reg-event-db reg-event-fx]]
            [kiwi.utils :as utils]))

(reg-event-fx
 :assoc-editing?
 (fn [{:keys [db]} [_ editing?]]
   (if editing?
      {:db (-> db
               (assoc-in [:route-state :editing?] editing?)
               (assoc-in [:route-state :edit-state]
                         {:contents (get-in db [:route-state :page :contents])}))}
      {:db (-> db
               (assoc-in [:route-state :editing?] editing?)
               (utils/dissoc-in [ :route-state :edit-state]))
       :dispatch [:save-page
                  (get-in db [:route-state :edit-state :contents])]})))
  
(reg-event-db
 :edit-page
 (path :route-state :edit-state)
  (fn [edit-state [_ contents]]
    (assoc edit-state :contents contents)))
