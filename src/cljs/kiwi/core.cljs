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
            [kiwi.page.views]
            [kiwi.page.modals]
            [kiwi.views :as views]
            [kiwi.routes :as routes]
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

;; ** JavaScript imports

(def mousetrap (js/require "mousetrap"))

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

;; * Keybindings

(defn toggle-editing! []
  (dispatch [:assoc-editing? (not @(subscribe [:editing?]))]))

(defn escape! []
  (when @(subscribe [:editing?])
    (toggle-editing!))
  (.blur js/document.activeElement)
  (dispatch [:hide-modal]))

(def scroll-by 50)

(def page-scroll-by 300)

(def keybindings
  [{:key "e"
    :keymap :local
    :handler #(when (not @(subscribe [:editing?]))
                (toggle-editing!))}
   {:key "i"
    :keymap :local
    :handler #(when (not @(subscribe [:editing?]))
                (toggle-editing!))}

   {:key "command+enter"
    :keymap :global
    :handler toggle-editing!}
   {:key "g s"
    :keymap :local
    :handler #(dispatch [:set-route (routes/search-route)])}
   {:key "g h"
    :keymap :local
    :handler #(dispatch [:set-route (routes/page-route {:permalink "home"})])}
   {:key "n"
    :keymap :local
    :handler #(dispatch [:show-modal :add-page])}
   {:key "esc"
    :keymap :global
    :handler escape!}
   {:key "ctrl+["
    :keymap :global
    :handler escape!}
   {:key "u"
    :keymap :local
    :handler #(.scrollBy js/window 0 (- page-scroll-by))}
   {:key "d"
    :keymap :local
    :handler #(.scrollBy js/window 0 page-scroll-by)}
   {:key "j"
    :keymap :local
    :handler #(.scrollBy js/window 0 scroll-by)}
   {:key "k"
    :keymap :local
    :handler #(.scrollBy js/window 0 (- scroll-by))}
   {:key "H"
    :keymap :local
    :handler #(.back js/window.history)}
   {:key "L"
    :keymap :local
    :handler #(.forward js/window.history)}])

(defn register-keybindings! [keybindings]
  "Register some vim-esque keybindings for navigation"
  (doseq [{:keys [key keymap handler] :as binding} keybindings]
    (cond
      (= keymap :local) (.bind mousetrap key handler)
      (= keymap :global) (.bindGlobal mousetrap key handler))))

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
  (let [wiki-root-dir (storage/load "wiki-root-dir")
        google-access-token (storage/load "google-access-token")]
    (dispatch-sync [:initialize
                    {:wiki-root-dir wiki-root-dir
                     :google-access-token google-access-token}]) 
    (register-keybindings! keybindings)

    (if (not (nil? wiki-root-dir))
      (dispatch [:set-route (routes/page-route {:permalink "home"})])
      (dispatch [:set-route (routes/settings-route)]))
    (render)))
