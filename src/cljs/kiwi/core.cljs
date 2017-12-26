;; * Imports
;; ** Clojurescript imports
(ns kiwi.core
  (:require [cljs-time.coerce :as coerce]
            [cljs-time.format :as f]
            [cljs.core.async :refer [<! chan pipe put! timeout]]
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
            [kiwi.editor.views]
            [pushy.core :as pushy]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame :refer [after dispatch dispatch-sync enrich path register-handler register-sub subscribe]]
            [re-frame.db :refer [app-db]]
            [reagent.core :as reagent]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [kiwi.markdown-processors :as markdown-processors]
            [cljs.core.async :as async]
            [kiwi.google-calendar :as google-calendar])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]
   [reagent.ratom :refer [reaction]])
  (:import goog.History))

;; ** JavaScript imports

(def mousetrap (js/require "mousetrap"))
(def electron (js/require "electron"))
(def shell (.-shell electron))
(def remote (.-remote electron))
(def dialog (.-dialog remote))
(def sugar (js/require "sugar-date"))
(set! (.-sugar js/window) sugar)
(def lunr (js/require "lunr"))
(set! (.-lunr js/window) lunr)

(extend-type js/NodeList
  ISeqable
    (-seq [array] (array-seq array 0)))

;; * Feature flags

(def schedule-enabled? false)
(def tags-enabled? false)

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

(secretary/set-config! :prefix "#")

(secretary/defroute index-route "/" []
  (dispatch [:navigate [:home-page]]))

(secretary/defroute settings-route "/settings" []
  (dispatch [:navigate [:settings-page]]))

(defn dispatch-search-page [filter]
  (go
    (let [pages (<! (page-db/load-all!))]
      (dispatch [:navigate [:search-page]
                 {:pages pages
                  :filter filter}]))))

(secretary/defroute search-route "/search" [_ query-params]
  (dispatch-search-page (get query-params :filter "")))

(secretary/defroute page-route "/page/:permalink" [permalink]
  (go
    (let [maybe-page (<! (page-db/load permalink))
          page (if (= maybe-page :not-found) 
                 (page/new-page permalink) 
                 maybe-page)
          permalinks (<! (page-db/load-permalinks))]
      (dispatch [:navigate [:wiki-page-view permalink]
                 {:page page :permalinks permalinks :editing? false}]))))

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

(defn dispatch-new-page! []
  (dispatch [:show-modal :add-page]))

(defn layout-header []
  [:div.header
   [:div [:a.btn.btn-default {:href (page-route {:permalink "home"})} [:i.fa.fa-home] " Home"]]
   [:nav.navigation
    [:div.btn-group
     [:a.btn.btn-default {:href (settings-route)} [:i.fa.fa-cog] " Settings"]
     [:a.btn.btn-default {:href (search-route)} [:i.fa.fa-search] " Search"]
     [:button.btn.btn-default
      {:on-click dispatch-new-page!}
      [:i.fa.fa-plus] " New page"]]]])

;; ** Modals

(defn modal [content]
  [re-com/modal-panel
   :child content
   :backdrop-on-click #(dispatch [:hide-modal])])

(defn add-page-form
  "A form that lets a user specify options for creating a page."
  []
  (let [page-name (reagent/atom "")]
    (fn []
      [re-com/v-box
       :gap "10px"
       :width "400px"
       :children [[:h3 "New page"]
                  [re-com/label
                   :label [:p
                           "Create a new page by giving it a name. You can also create pages by clicking a "
                           [:code "[[Wiki Link]]"]
                           " to a page that doesn't exist yet."]
                   ]
                  [re-com/input-text
                   :model page-name
                   :width "auto"
                   :attr {:auto-focus true}
                   :on-change #(reset! page-name %)
                   :change-on-blur? false
                   :placeholder "Name of the page"]
                  [re-com/label
                   :label
                   (when-not (string/blank? @page-name)
                             [:p "You'll be able to link to the page by typing "
                              [:code "[[" (page/capitalize-words @page-name) "]]"]
                              "."])]
                  [re-com/button
                   :label "Add"
                   :class "btn-success"
                   :disabled? (string/blank? @page-name)
                   :on-click (fn [_]
                               (re-frame/dispatch [:create-page @page-name])
                               (re-frame/dispatch [:hide-modal]))]]])))

(defn add-page-modal []
  [modal 
   [add-page-form]])

(defn delete-page-modal []
  (let [page (subscribe [:current-page])]
    (fn []
      [modal
       [re-com/v-box
        :gap "10px"
        :width "400px"
        :children [[:h3 "Are you sure?"]
                   [:p "You are about to delete " [:b (page/title @page)] "."]
                   [re-com/h-box
                    :children [[re-com/button
                                :label "Cancel"
                                :class "btn-default"
                                :style {:margin-right "10px"}
                                :on-click #(re-frame/dispatch [:hide-modal])]
                               [re-com/button
                                :label "Delete it"
                                :class "btn-danger"
                                :on-click (fn [_]
                                            (go
                                              (let [deleted (<! (page-db/delete! @page))]
                                                (when (= deleted :deleted)
                                                  (re-frame/dispatch [:assoc-editing? false])
                                                  (re-frame/dispatch [:hide-modal])
                                                  (.back js/window.history)))))]]]]]])))



(defn schedule-page-form
  "A form that lets a user specify a date and time to schedule a page"
  []
  (let [page (subscribe [:current-page])
        date-string (reagent/atom "")]
    (when (get @page :scheduled)
      (reset! date-string
              (-> (get @page :scheduled)
                  (sugar.Date.)
                  (.full)
                  (.-raw)))
      )
    (fn []
      [re-com/v-box
       :gap "10px"
       :width "400px"
       :children [[:h3 "Schedule page"]
                  [:p "Scheduling a page will add it as an event to Google Calendar."]
                  [re-com/input-text
                   :model date-string
                   :width "auto"

                   :on-change #(reset! date-string %)
                   :attr {:auto-focus true}
                   :change-on-blur? false
                   :placeholder "3pm tomorrow"]
                  [re-com/label
                   :label
                   (when-not (string/blank? @date-string)
                     [:p [:i (str (sugar.Date.create @date-string))]])]
                  [re-com/button
                   :label "Schedule"
                   :class "btn-success"
                   :disabled? (= (str (sugar.Date.create @date-string)) "Invalid Date")
                   :on-click (fn [_]
                               (re-frame/dispatch [:schedule-page @page (sugar.Date.create @date-string)])
                               (re-frame/dispatch [:hide-modal]))]]])))

(defn schedule-page-modal []
  [modal
   [schedule-page-form]])


(defmulti modals identity)
(defmethod modals :add-page [] [add-page-modal])
(defmethod modals :delete-page [] [delete-page-modal])
(defmethod modals :schedule-page [] [schedule-page-modal])

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
   (when tags-enabled?
     [:ul
      (merge {:className "tags-list"} opts)
      (map (fn [tag] ^{:key tag}
             [:li
              [re-com/button
               :class "btn-tag"
               :label (str "#" tag)
               :on-click #(dispatch [:set-route (search-route
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
          (when schedule-enabled?
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

(defn base-layout [content]
  (let [modal (subscribe [:modal])]
    (fn [content] 
      [:div
       [layout-header]
       [:section.content-wrapper
        [:div.content
         content]]
       (when @modal
         (modals @modal))])))

(defn wiki-page []
  (let [page (subscribe [:current-page])]
    (reagent/create-class
     {:reagent-render 
      (fn []
        [base-layout
         [wiki-page-contents page]])})))

;; ** Settings

(defn set-wiki-root-button []
  (let [on-directory-chosen
        (fn [directories]
          (when (seq directories)
            (dispatch [:assoc-wiki-root-dir (first (js->clj directories))])))]
    [:button.btn.btn-default
     {:on-click (fn [_]
                  (.showOpenDialog dialog
                                   (clj->js {:properties ["openDirectory"]})
                                   on-directory-chosen))}
     "Set wiki location"]))

(defn link-with-google-button
  ([text]
   [:button.btn.btn-default
    {:on-click (fn [_]
                 (go
                   (let [token (async/<! (google-calendar/sign-in))]
                     (dispatch [:assoc-google-access-token token]))))}
    text])
  ([] 
   [link-with-google-button "Link with Google"]))


(defn settings-page []
  (let [wiki-root-dir (subscribe [:wiki-root-dir])
        google-access-token (subscribe [:google-access-token])]
    (fn []
      [base-layout
       [:article#page.settings
        [:section 
         [:h1.post-title "Settings"]
         [:h2 "Wiki location"]
         (when (not (nil? @wiki-root-dir))
           [:p [ :code @wiki-root-dir]])
         [set-wiki-root-button]]
        (when schedule-enabled?
          [:section
           [:h2 "Link with Google Calendar"]
           (if @google-access-token
             [:div 
              [:p "Already linked with Google."]
              [link-with-google-button "Re-link with Google"]]
             [link-with-google-button])])]])))

;; ** Search

(defn page-list-item [page]
  (let [title (:title page)
        tags (:tags page)
        contents (:contents page)
        preview (->> (string/split contents #" ")
                     (take 20)
                     (string/join " "))
        permalink (:permalink page)
        date-format (f/formatter "MMMM d, yyyy")
        date (f/unparse date-format (coerce/from-date (:timestamp page)))
        path (str "#/page/" permalink)]
    ^{:key permalink}
    [:li
     [:div.row 
      [:div.col-xs
       [:h3.float-xs-left [:a.page-link.internal {:href path} title]]
       [:span.page-date.float-xs-right date]]]
     [:div.row
      [:div.col-xs
       [:p.page-preview (str preview "...")]
       [tags-list {:className "tags-preview"} tags]]]]))

(defn search-page []
  (let [filtered-pages (subscribe [:filtered-pages])
        search-text (subscribe [:search-filter])]
    (reagent/create-class
     {:component-did-mount 
      (fn []
        (print "did-mount"))
      :reagent-render
      (fn []
        [base-layout
          [:article#page
           [:h1.post-title "Search"]
           [re-com/input-text
            :attr {:auto-focus true}
            :change-on-blur? false
            :model search-text
            :width "100%"
            :style {:font-size "16px"}
            :placeholder "Search..."
            :on-change #(dispatch [:assoc-search-filter %])]
           [:ul.page-list
            (map page-list-item @filtered-pages)]]])})))

;; ** Home page

(defn home-page []
  [base-layout
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
  [settings-page])

(defmethod page :search-page [_ _]
  [search-page])

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
    :handler #(dispatch [:set-route (search-route)])}
   {:key "g h"
    :keymap :local
    :handler #(dispatch [:set-route (page-route {:permalink "home"})])}
   {:key "n"
    :keymap :local
    :handler dispatch-new-page!}
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
              (secretary/dispatch! (.-token event))))
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
      (dispatch [:set-route (page-route {:permalink "home"})])
      (dispatch [:set-route (settings-route)]))
    (render)))
