(ns reagent-sample.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :as re-frame :refer [register-sub]]))

(register-sub 
  :all-pages
  (fn [db] (reaction (get-in @db [:current-route 1 :pages]))))

(register-sub :current-page
  (fn [db] (reaction (get-in @db [:current-route 1 :page]))))

(register-sub :linked-with-dropbox?
   (fn [db] (reaction (get-in @db [:linked-with-dropbox?]))))
