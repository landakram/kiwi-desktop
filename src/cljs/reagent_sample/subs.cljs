(ns reagent-sample.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :as re-frame :refer [register-sub]]))

(register-sub 
  :all-pages
  (fn [db] (reaction (get-in @db [:route-state :pages]))))

(register-sub
 :search-filter
 (fn [db] (reaction (get-in @db [:route-state :filter]))))

(register-sub 
  :permalinks
  (fn [db] (reaction (get-in @db [:route-state :permalinks]))))

(register-sub
 :editing?
 (fn [db] (reaction (get-in @db [:route-state :editing?]))))

(register-sub :current-page
  (fn [db] (reaction (get-in @db [:route-state :page]))))

(register-sub :wiki-root-dir
   (fn [db] (reaction (get-in @db [:wiki-root-dir]))))

(register-sub :modal
              (fn [db _]
                (reaction (get-in @db [:route-state :modal]))))
