(ns kiwi.subs
  (:require [kiwi.utils :as utils]
            [re-frame.core :as re-frame :refer [reg-sub]]
            [kiwi.markdown-processors :as markdown-processors]
            [kiwi.editor.subs]
            [kiwi.search.subs]
            [kiwi.settings.subs]
            [kiwi.setup.subs]
            [kiwi.page.subs]
            [clojure.string :as string]))

(reg-sub
 :modal
 (fn [db _]
   (get-in db [:route-state :modal])))

(reg-sub
 :current-route 
 (fn [db _] 
   (get-in db [:current-route])))
