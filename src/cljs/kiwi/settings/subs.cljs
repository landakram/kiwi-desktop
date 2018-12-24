(ns kiwi.settings.subs
  (:require [re-frame.core :as re-frame :refer [reg-sub]]
            [re-frame.db :refer [app-db]]
            [kiwi.setup.utils :as setup-utils]))

(reg-sub
 :wiki-root-dir
 (fn [db _]
   (get-in db [:wiki-root-dir])))

(defn configured? [db _]
  (let [wiki-root-dir (get-in db [:wiki-root-dir])]
    (and
     (not (nil? wiki-root-dir)))))

(reg-sub
 :configured?
 configured?)

(reg-sub
 :google-access-token
 (fn [db _]
   (get-in db [:google-access-token])))
