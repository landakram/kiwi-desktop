(ns kiwi.subs
  (:require [kiwi.utils :as utils]
            [re-frame.core :as re-frame :refer [reg-sub]]
            [kiwi.markdown-processors :as markdown-processors]
            [kiwi.editor.subs]
            [kiwi.search.subs]
            [kiwi.settings.subs]
            [clojure.string :as string]))

(reg-sub
 :permalinks
 (fn [db _]
   (get-in db [:route-state :permalinks])))

(reg-sub
 :editing?
 (fn [db _]
   (get-in db [:route-state :editing?])))

(reg-sub
 :current-page
 (fn [db _]
   (get-in db [:route-state :page])))

(reg-sub
 :modal
 (fn [db _]
   (get-in db [:route-state :modal])))

(reg-sub
 :current-route 
 (fn [db _] 
   (get-in db [:current-route])))

(reg-sub
 :current-page-ast
 :<- [:current-page]
 :<- [:permalinks]
 (fn [[  current-page permalinks]]
   (markdown-processors/get-ast (:contents current-page))))

