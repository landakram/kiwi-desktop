(ns reagent-sample.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :as re-frame :refer [register-sub]]))

(register-sub 
  :all-pages
  (fn [db] (reaction (get-in @db [:route-state :pages]))))

(register-sub 
  :permalinks
  (fn [db] (reaction (get-in @db [:route-state :permalinks]))))

(register-sub :current-page
  (fn [db] (reaction (get-in @db [:route-state :page]))))

(register-sub :linked-with-dropbox?
   (fn [db] (reaction (get-in @db [:linked-with-dropbox?]))))

(register-sub :modal
              (fn [db _]
                (reaction (get-in @db [:route-state :modal]))))
