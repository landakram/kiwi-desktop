;; * Imports
;; ** Clojurescript imports
(ns kiwi.core
  (:require [cljs.core.async :refer [<! chan pipe put! timeout]]
            [clojure.string :as string]
            [goog.events :as events]
            [goog.history.EventType :as HistoryEventType]
            [kiwi.db :as page-db]
            [kiwi.handlers :as handlers]
            [kiwi.history :refer [history]]
            [kiwi.page.core :as page]
            [kiwi.storage :as storage]
            [kiwi.subs :as subs]
            [kiwi.utils :as utils]
            [kiwi.features :as features]
            [kiwi.editor.views]
            [kiwi.search.views]
            [kiwi.settings.views]
            [kiwi.setup.views]
            [kiwi.page.views]
            [kiwi.page.modals]
            [kiwi.views :as views]
            [kiwi.routes :as routes]
            [kiwi.keybindings :refer [register-keybindings!]]
            [pushy.core :as pushy]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame :refer [after dispatch dispatch-sync enrich path register-handler register-sub subscribe]]
            [re-frame.db :refer [app-db]]
            [reagent.core :as reagent]
            [reagent.session :as session]
            [kiwi.markdown-processors :as markdown-processors]
            [cljs.core.async :as async]
            [kiwi.google-calendar :as google-calendar])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]
   [reagent.ratom :refer [reaction]])
  (:import goog.History))

(extend-type js/NodeList
  ISeqable
    (-seq [array] (array-seq array 0)))

;; ** Home page

(defn home-page []
  [views/base-layout
   [:article#page]])

;; ** Wiring

(defmulti page
  (fn [name _]
    name))

(defmethod page :home-page [_ _] 
  [home-page])

(defmethod page :wiki-page-view [_ _]
  [kiwi.page.views.wiki-page])

(defmethod page :settings-page [_ _]
  [kiwi.settings.views.settings-page])

(defmethod page :search-page [_ _]
  [kiwi.search.views.search-page])

(defn app []
  (let [current-route (subscribe [:current-route])]
    (apply page @current-route)))

(defn render [] 
  (reagent/render [app] (.getElementById js/document "app")))

;; * Main

(defn hook-browser-navigation! []
  (doto (History.)
        (events/listen
          HistoryEventType/NAVIGATE
          (fn [event]
            (routes/dispatch! (.-token event))))
        (.setEnabled true)))

(defn ^:export init []
  (enable-console-print!)
  (set! *warn-on-infer* true)
  (hook-browser-navigation!)
  (let [wiki-root-d (storage/load "wiki-root-dir")
        wiki-root-dir (if (= "null" wiki-root-d) nil wiki-root-d)
        google-access-token (storage/load "google-access-token")]
    (dispatch-sync [:initialize
                    {:wiki-root-dir wiki-root-dir
                     :google-access-token google-access-token}]) 
    (register-keybindings!)

    (if (not (nil? wiki-root-dir))
      (dispatch-sync [:set-route (routes/page-route {:permalink "home"})])
      (dispatch-sync [:set-route (routes/settings-route)]))
    (render)))
