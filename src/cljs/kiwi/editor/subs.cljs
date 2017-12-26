(ns kiwi.editor.subs
  (:require 
   [re-frame.core :as re-frame :refer [reg-sub]]))

(reg-sub
 :edited-contents
 (fn [db _]
   (get-in db [:route-state :edit-state :contents])))
