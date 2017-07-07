(ns reagent-sample.core
    (:require-macros [reagent.ratom :refer [reaction]]
                     [cljs.core.async.macros :refer [go-loop go]])
    (:require [reagent.core :as reagent]
              [reagent.session :as session]
              [re-frame.db :refer [app-db]]
              [re-com.core :as re-com]
              [re-frame.core :as re-frame
               :refer [dispatch
                       dispatch-sync
                       register-sub
                       subscribe
                       register-handler
                       after
                       enrich
                       path]]
              [secretary.core :as secretary :include-macros true]
              [goog.events :as events]
              [pushy.core :as pushy]
              [clojure.string :as string]
              [goog.history.EventType :as HistoryEventType]
              [tailrecursion.cljson :refer [clj->cljson cljson->clj]]
              [cljs.core.async :refer [chan <! put! pipe timeout]]
              [cljs-time.format :as f]
              [cljs-time.coerce :as coerce]
              [reagent-sample.subs :as subs]
              [reagent-sample.handlers :as handlers]
              [reagent-sample.storage :as storage]
              [reagent-sample.db :as page-db]
              [reagent-sample.channels :as channels]
              [reagent-sample.utils :as utils]
              [reagent-sample.history :refer [ history]]
              [reagent-sample.page :as page])
    (:import goog.History))

;; -------------------------
;; Views


(def mousetrap (js/require "mousetrap"))

(def electron (js/require "electron"))
(def shell (.-shell electron))
(def remote (.-remote electron))
(def dialog (.-dialog remote))
(def lunr (js/require "lunr"))
(set! (.-lunr js/window) lunr)

(def Markdown (js/require "markdown-it"))
(def markdown-renderer (Markdown.))
(.use markdown-renderer (js/require "markdown-it-task-lists"))
(set! (.-markdown js/window) markdown-renderer)

(extend-type js/NodeList
  ISeqable
    (-seq [array] (array-seq array 0)))

(defonce img-cache (atom {}))

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
            ; If the image is a local one
            ; Check the img cache to see if we already have a object URL for it
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

(defn rewrite-internal-links [permalinks html-node]
  (let [nodes (.querySelectorAll html-node "a.internal")]
    (doseq [node nodes]
      (let [page-name (.-innerHTML node)
            permalink (page/get-permalink-from-title page-name)
            classes (page/construct-classes permalink permalinks)
            class-str (string/join " " classes)]
        (set! (.-class node) class-str)))
    nodes)
  )

(defn markdown->html [wiki-root-dir markdown permalinks]
    (let [html-contents (->> markdown
                            str
                            (.render markdown-renderer)
                            (#(page/parse-wiki-links % permalinks))
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

(secretary/set-config! :prefix "#")

(secretary/defroute index-route "/" []
  (dispatch [:navigate [:home-page]]))

(secretary/defroute settings-route "/settings" []
  (dispatch [:navigate [:settings-page]]))

(secretary/defroute search-route "/search" []
  (go
    (let [pages (<! (page-db/load-all!))]
      (dispatch [:navigate [:search-page]
                 {:pages pages
                  :filter ""}]))))

(secretary/defroute about-route "/about" []
  (dispatch [:navigate [:about-page]]))

(secretary/defroute page-route "/page/:permalink" [permalink]
  (go
    (let [maybe-page (<! (page-db/load permalink))
          page (if (= maybe-page :not-found) 
                 (page/new-page permalink) 
                 maybe-page)
          permalinks (<! (page-db/load-permalinks))]
      (dispatch [:navigate [:wiki-page-view permalink]
                 {:page page :permalinks permalinks :editing? false}]))))


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
           (rewrite-internal-links @permalinks node)
           (highlight-code node)))
       :component-did-mount
       (fn [this]
         (let [node (reagent/dom-node this)]
           (js/window.renderMath)
           (rewrite-internal-links @permalinks node)
           (highlight-code node)))})))


(defn page-title-field [page]
  (let [{:keys [title]} @page]
    [:h1.post-title title]))


; Save updated pages to localStorage
(go-loop []
  (let [page (<! channels/page-changes)]
    (when page
      (page-db/save! page)
      (recur))))

(defn page-content-field []
  (let [local-state (atom {})]
    (reagent/create-class
      {:reagent-render 
      (fn []
        [:textarea 
          {:style {:width "100%" :height "500px" :display "none"}}])
      :component-did-mount 
      (fn [this]
        (let [{:keys [on-change contents]} (reagent/props this)
              node (reagent/dom-node this)
              ;; Adds syntax highlighting for [[Internal Links]]
              token-fn (fn [stream state] 
                        (if-let [match (when (.match stream "[[")
                                        (loop [ch (.next stream)]
                                          (if (and (= ch "]") (= (.next stream) "]"))
                                            (do (.eat stream "]")
                                                "internal-link")
                                            (when-not (nil? ch) (recur (.next stream))))))]
                          match
                          (do
                            (while (and (.next stream) 
                                      (not (.match stream "[[" false))
                                      (not (.match stream "\n" false)))
                              1))))
              mode (.defineMode js/CodeMirror "kiwi" 
                     (fn [config, parser-config]
                       (.overlayMode js/CodeMirror (.getMode js/CodeMirror 
                                               config 
                                               (or (.-backdrop parser-config) "gfm"))
                                     #js {:token token-fn})))
              editor (js/CodeMirror #(.appendChild (.-parentNode node) %)
                                    (clj->js
                                      {:value contents
                                        :mode "kiwi"
                                        :theme "default"
                                       :autofocus true
                                        :viewportMargin js/Infinity
                                        :lineWrapping true}))]
          (swap! local-state assoc :editor editor)
          (swap! local-state assoc :value contents)
          (.on editor "change" 
            (fn [instance change-obj]
              (let [new-value (.getValue instance)]
                (swap! local-state assoc :value new-value)
                (on-change new-value))))))
      :component-will-receive-props
      (fn [this new-argv]
        (let [{:keys [contents]} (reagent.impl.util/extract-props new-argv)
              {:keys [editor value]} @local-state]
          (when-not (= value contents) 
            (.setValue (.-doc (:editor @local-state)) contents))))})))

(defn editor [{:keys [page editing]}]
  [:div#edit-container 
    [:div#edit
     [page-title-field page]
     [page-content-field {:contents (:contents @page) 
                          :on-change #(dispatch-sync [:page-edit %])}]]])

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
      [:i.fa.fa-plus] " New page"]
     ]]])

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

(defmulti modals identity)
(defmethod modals :add-page [] [add-page-modal])
(defmethod modals :delete-page [] [delete-page-modal])

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

(defn delete-button [page editing]
  (fn [page]
    [:button.btn.btn-danger {:on-click
                             (fn [e]
                               (dispatch [:show-modal :delete-page]))}
     [:i.fa.fa-trash]
     " Delete"]))

(defn wiki-page-contents [page]
  (let [editing (subscribe [:editing?])]
    (fn [page]
      (let [{:keys [title contents]} @page] 
        [:div
         [:div.btn-group.pull-right
          (when @editing
            [delete-button page editing])
          [edit-button editing]]
          (if-not @editing
            [:article#page
             [:h1.post-title title]
             [:article [markdown-content contents]]]
            [editor {:page page :editing @editing}])]))))

(defn wiki-page []
  (let [page (subscribe [:current-page])]
    (reagent/create-class
     {:reagent-render 
      (fn []
        [base-layout
         [wiki-page-contents page]])})))


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

(defn settings-page []
  (let [wiki-root-dir (subscribe [:wiki-root-dir])]
    (fn []
      [base-layout
       [:article#page
        [:h1.post-title "Settings"]
        [:h2 "Wiki location"]
        (when (not (nil? @wiki-root-dir))
          [:p [ :code @wiki-root-dir]])
        [set-wiki-root-button]]])))


(defn page-list-item [page]
  (let [title (:title page)
        contents (:contents page)
        preview (->> (string/split contents #" ")
                     (take 20)
                     (string/join " "))
        permalink (:permalink page)
        date-format (f/formatter "MMMM d, yyyy")
        date (f/unparse date-format (coerce/from-date (:timestamp page)))
        path (str "#/page/" permalink)]
    [:li
     [:div.row 
      [:div.col-xs
       [:h3.float-xs-left [:a.page-link.internal {:href path} title]]
       [:span.page-date.float-xs-right date]]]
     [:div.row
      [:div.col-xs
       [:p.page-preview (str preview "...")]]]]))

(defn filter-pages [index pages filter-str]
  (let [results (js->clj (.search index (str filter-str)))
        permalinks (map #(get % "ref") results)
        filtered-pages (filter #(utils/in? permalinks (:permalink %)) pages)]

    (if (> (.-length filter-str) 0)
      filtered-pages
      pages)
    ))

(defn build-index [pages]
  (let [index (lunr (fn []
                      (this-as this
                        (.ref this "permalink")
                        (.field this "title")
                        (.field this "contents")

                        (doseq [page pages]
                          (.add this (clj->js page))))))]
    (print "rebuilding index")
    #_(set! (.-index js/window) index)
    index))

(defn search-page []
  (let [pages (subscribe [:all-pages])
        search-text (subscribe [:search-filter])
        index (atom (build-index @pages))]
    (reagent/create-class
     {:component-did-mount 
      (fn []
        (go 
          (reset! index (build-index @pages))))
      :reagent-render
      (fn []
        (let [filtered-pages (filter-pages @index @pages @search-text)]
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
             (map page-list-item filtered-pages)]]]))})))

(defn home-page []
  [base-layout
   [:article#page]])

(defn about-page []
  [:div [:h1 "This is an about page"]
   [:div [:a {:href "/"} "Home Page"]]])

;; -------------------------
;; Routes
(register-sub :current-route 
              (fn [db] 
                (reaction (get-in @db [:current-route]))))

(defmulti page
  (fn [name _]
    name))

(defmethod page :about-page [_ _] 
  [about-page])

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

(defn hook-browser-navigation! []
  (doto (History.)
        (events/listen
          HistoryEventType/NAVIGATE
          (fn [event]
              (secretary/dispatch! (.-token event))))
        (.setEnabled true)))

(defn toggle-editing! []
  (dispatch [:assoc-editing? (not @(subscribe [:editing?]))]))

(defn escape! []
  (when @(subscribe [:editing?])
    (toggle-editing!))
  (.blur js/document.activeElement)
  (dispatch [:hide-modal]))

(def scroll-by 50)

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
    :handler #(secretary/dispatch! (search-route))}
   {:key "g h"
    :keymap :local
    :handler #(secretary/dispatch! (page-route {:permalink "home"}))}
   {:key "n"
    :keymap :local
    :handler dispatch-new-page!}
   {:key "esc"
    :keymap :global
    :handler escape!}
   {:key "ctrl+["
    :keymap :global
    :handler escape!}
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

#_(register-keybindings! keybindings)

(defn ^:export init []
  (enable-console-print!)
  (hook-browser-navigation!)
  (let [wiki-root-dir (storage/load "wiki-root-dir")]
    (dispatch-sync [:initialize {:wiki-root-dir wiki-root-dir}]) 
    (register-keybindings! keybindings)

    (if(not (nil? wiki-root-dir))
      (secretary/dispatch! (page-route {:permalink "home"}))
      (secretary/dispatch! (settings-route)))
    (render)))
