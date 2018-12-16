(ns kiwi.settings.subs
  (:require [re-frame.core :as re-frame :refer [reg-sub]]))

(reg-sub
 :wiki-root-dir
 (fn [db _]
   (get-in db [:wiki-root-dir])))

(reg-sub
 :configured?
 :<- [:wiki-root-dir]
 (fn [[wiki-root-dir] _]
   (not (nil? wiki-root-dir))))

(reg-sub
 :google-access-token
 (fn [db _]
   (get-in db [:google-access-token])))
