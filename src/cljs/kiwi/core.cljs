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
            [kiwi.page :as page]
            [kiwi.storage :as storage]
            [kiwi.subs :as subs]
            [kiwi.utils :as utils]
            [kiwi.features :as features]
            [kiwi.editor.views]
            [kiwi.search.views]
            [kiwi.settings.views]
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
(def sugar (js/require "sugar-date"))
(set! (.-sugar js/window) sugar)
(def lunr (js/require "lunr"))
(set! (.-lunr js/window) lunr)

(extend-type js/NodeList
  ISeqable
    (-seq [array] (array-seq array 0)))

;; * Feature flags

;; * Markdown utility functions

(defn path->filename [path]
  (-> path
      (string/split "/")
      last))

(defn create-dummy-node [html-contents]
  (let [el (.createElement js/document "div")]
    (set! (.-innerHTML el) html-contents)
    el))

(defn img-tag->path [root-dir img-tag]
  (let [src (string/lower-case (.-src img-tag))
        filename (path->filename src)
        path (str root-dir "/" page-db/img-rel-path filename)]
    path))

(defn is-local-img [src]
  (.startsWith src "file://"))

(defn rewrite-image-paths [root-dir html-contents]
  "Rewrite relative image paths like 'img/foo.jpg' to {root-dir}/public/img/foo.jpg"
  (let [el (create-dummy-node html-contents)
        img-nodes (.querySelectorAll el "img")]
        (doseq [img img-nodes]
          (let [src (.-src img)
                path (img-tag->path root-dir img)]
            (when (is-local-img src)
              (set! (.-src img) path))))
        (.-innerHTML el)))

(defn rewrite-external-links [html-contents]
  "Rewrite external links to open in a new window. Electron is set up to open these in the default browser."
  (let [el (create-dummy-node html-contents)
        links (.querySelectorAll el "a:not(.internal)")]
    (doseq [link links]
      (let [href (.-href link)]
        (set! (.-target link) "_blank")))
    (.-innerHTML el)))

(defn attach-checkbox-handlers [html-node]
  (let [nodes (.querySelectorAll html-node "input[type=checkbox]")]
    (doseq [node nodes]
      (set! (.-disabled node) false)
      (set! (.-onclick node)
            (fn [e]
              (this-as this
                (dispatch [:checkbox-toggle [(.-id (.-parentNode this))]])
                (.preventDefault e)))))
    nodes))

(defn markdown->html [wiki-root-dir markdown permalinks]
  (let [^js/unified processor (markdown-processors/html-processor permalinks)
        html-contents (->> markdown
                            str
                            (.processSync processor)
                            (.toString)
                            (rewrite-image-paths wiki-root-dir)
                            (rewrite-external-links))]

    html-contents))

(defn highlight-code [html-node]
  (let [nodes (.querySelectorAll html-node "pre code")]
    (loop [i (.-length nodes)]
      (when-not (neg? i)
        (when-let [item (.item nodes i)]
          (.highlightBlock js/hljs item))
        (recur (dec i))))))

;; * Routes

;; * Views
;; ** Editor

(defn edit-button [editing]
  [:button.edit-button.btn.btn-default
   {:on-click (fn [] (dispatch [:assoc-editing?  (not @editing)]))} 
   (if-not @editing
     [:i.fa.fa-pencil]
     [:i.fa.fa-check])
   (if-not @editing
     " Edit"
     " Done")])

(defn close-button [on-click]
  [:button.close {:on-click on-click
                  :dangerouslySetInnerHTML {:__html "<span>&times;</span>"}}])

;; ** Navigation bar

;; ** Modals

;; ** Wiki Page

(defn markdown-content [content]
  (let [wiki-root-dir (subscribe [:wiki-root-dir])
        permalinks (subscribe [:permalinks])]
    (reagent/create-class
      {:reagent-render 
       (fn [content]
         [:div
          {:dangerouslySetInnerHTML
           {:__html (markdown->html @wiki-root-dir content @permalinks)}}])
       :component-did-update
       (fn [this]
         (let [node (reagent/dom-node this)]
           (js/window.renderMath)
           (attach-checkbox-handlers node)
           (highlight-code node)))
       :component-did-mount
       (fn [this]
         (let [node (reagent/dom-node this)]
           (js/window.renderMath)
           (attach-checkbox-handlers node)
           (highlight-code node)))})))


(defn delete-button [page editing]
  (fn [page]
    [:button.btn.btn-danger {:on-click
                             (fn [e]
                               (dispatch [:show-modal :delete-page]))}
     [:i.fa.fa-trash]
     " Delete"]))

(defn schedule-button [js-start-date]
  (fn [js-start-date]
    (js/console.log js-start-date)
    [:button.btn.btn-default {:on-click
                             (fn [e]
                               (dispatch [:show-modal :schedule-page]))}

     [:i.fa.fa-calendar-plus-o]
     (if (not (nil? js-start-date))
       (str " " (-> js-start-date
                              (sugar.Date.)
                              (.full)))
       " Schedule")]))

(defn tags-list
  ([opts tags]
   (when features/tags-enabled?
     [:ul
      (merge {:className "tags-list"} opts)
      (map (fn [tag] ^{:key tag}
             [:li
              [re-com/button
               :class "btn-tag"
               :label (str "#" tag)
               :on-click #(dispatch [:set-route (routes/search-route
                                                 {:query-params {:filter (str "tags:" tag)}})])]])
           tags)]))
  ([tags]
   (tags-list {} tags)))

(defn wiki-page-contents [page]
  (let [editing (subscribe [:editing?])]
    (fn [page]
      (let [{:keys [title contents tags]} @page] 
        [:div
         [:div.btn-group.pull-right
          (when features/schedule-enabled?
            (js/console.log page)
            [schedule-button (get @page :scheduled)])
          (when @editing
            [delete-button page editing])
          [edit-button editing]]
         (if-not @editing
           [:article#page
            [:h1.post-title title]
            [tags-list tags]
            [:article [markdown-content contents]]]
           [kiwi.editor.views.editor {:page page :editing @editing}])]))))

(defn wiki-page []
  (let [page (subscribe [:current-page])]
    (reagent/create-class
     {:reagent-render 
      (fn []
        [views/base-layout
         [wiki-page-contents page]])})))

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
  [wiki-page])

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
