(ns reagent-sample.core
    (:require-macros [reagent.ratom :refer [reaction]]
                     [cljs.core.async.macros :refer [go-loop go]])
    (:require [reagent.core :as reagent]
              [reagent.session :as session]
              [re-frame.db :refer [app-db]]
              [re-frame.core :as re-frame :refer [dispatch dispatch-sync register-sub subscribe register-handler after enrich path]]
              [secretary.core :as secretary :include-macros true]
              [goog.events :as events]
              [pushy.core :as pushy]
              [clojure.string :as string]
              [goog.history.EventType :as EventType]
              [tailrecursion.cljson :refer [clj->cljson cljson->clj]]
              [cljs.core.async :refer [chan <! put! pipe timeout]]
              [cljs-time.format :as f]
              [cljs-time.coerce :as coerce]
              [reagent-sample.subs :as subs]
              [reagent-sample.handlers :as handlers]
              [reagent-sample.storage :as storage]
              [reagent-sample.db :as page-db]
              [reagent-sample.channels :as channels]
              [reagent-sample.sync :as sync]
              [reagent-sample.utils :as utils]
              [reagent-sample.page :as page]))

;; -------------------------
;; Views

(extend-type js/NodeList
  ISeqable
    (-seq [array] (array-seq array 0)))

(defonce img-cache (atom {}))

(defn create-dummy-node [html-contents]
  (let [el (.createElement js/document "div")]
    (set! (.-innerHTML el) html-contents)
    el))

(defn img-tag->path [img-tag]
  (let [src (string/lower-case (.-src img-tag))
        filename (sync/path->filename src)
        path (str "img/" filename)]
    path))

(defn is-local-img [src]
  (utils/contains src (.-hostname js/window.location)))

(defn load-images-from-cache [html-contents]
  (let [el (create-dummy-node html-contents)
        img-nodes (.querySelectorAll el "img")]
        (doseq [img img-nodes]
          (let [src (.-src img)
                path (img-tag->path img)]
            ; If the image is a local one
            ; Check the img cache to see if we already have a object URL for it
            (when (and (is-local-img src)
                       (not (utils/contains src "blob:")))
              (when-let [cached-src (get @img-cache path)]
                (set! (.-src img) cached-src)))))
        (.-innerHTML el)))

(defn load-images [html-contents]
  (let [ch (chan)
        el (create-dummy-node html-contents)
        img-nodes (.querySelectorAll el "img")]
    (go
      (doseq [img img-nodes]
        (let [src (.-src img)
              path (img-tag->path img)]
          ; If the image is a local one
          (when (and (is-local-img src)
                     (not (utils/contains src "blob:"))
                     ; Check the img cache to see if we already have a object URL for it
                     (not (get @img-cache path)))
            ; Otherwise, get the corresponding blob out of IndexedDB
            (let [image (<! (page-db/load-in "images" "path" path))
                  object-url (.createObjectURL js/URL (:contents image))]
                  ; and set it as the img src
              (swap! img-cache assoc path object-url)
              (set! (.-src img) object-url)))))
      (put! ch (.-innerHTML el)))
    ch))


(defn markdown->html [markdown]
    (let [html-contents (-> markdown
                            str
                            js/marked
                            page/parse-wiki-links
                            load-images-from-cache)]
    html-contents))

(defn watch-chan [atom]
  (let [ch (chan)]
    (add-watch atom (gensym) 
               (fn [_ _ prev current]
                 (when-not (= prev current)
                   (put! ch current))))
    ch))

(defn watch-in-chan [atom ks]
  (-> (watch-chan app-db)
      (pipe 
       (chan 1 
             (comp
                                        ; Only pipe if the atom actually contains the keys
              (filter #(utils/contains-in % ks))
                                        ; Finally, get the value of keys in the atom
              (map #(get-in % ks)))))))

(defn highlight-code [html-node]
  (let [nodes (.querySelectorAll html-node "pre code")]
    (loop [i (.-length nodes)]
      (when-not (neg? i)
        (when-let [item (.item nodes i)]
          (.highlightBlock js/hljs item))
        (recur (dec i))))))


(defn markdown-content [content]
  [(with-meta
     (fn [] [:div {:dangerouslySetInnerHTML
                   {:__html (markdown->html content)}}])
     {:component-did-mount
      (fn [this]
        (let [node (reagent/dom-node this)]
          (highlight-code node)))})])


(defn page-title-field [page]
  (let [{:keys [title]} @page]
    [:h1 title]))

;(def page-changes  
  ;(watch-in-chan app-db [:current-route 1 :page]))

;(go-loop []
  ;(let [page (<! page-changes)]
    ;(recur)))

; Save updated pages to localStorage
(go-loop []
  (let [page (<! channels/page-changes)]
    (page-db/save! page)
    (when (:dirty? page)
      (sync/write! page)
      (dispatch [:assoc-dirty? page false]))
    (recur)))

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
                                                (println "found link")
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
  (if-not editing 
    [:div] 
    [:div#edit-container 
     [:div#edit
      [page-title-field page]
      [page-content-field {:contents (:contents @page) 
                           :on-change #(dispatch-sync [:page-edit %])}]]]))

(defn layout-header []
  [:div.header
   [:div [:a {:href "/page/home"} "Home"]]
    [:nav.navigation
        [:ul
            [:li
            [:a {:href "/search"} "Search"]]
            [:li
            [:a {:href "/settings"} "Settings"]]
            ]]])

(defn wiki-page-contents [page]
  (let [editing (reagent/atom false)]
    (fn [page]
      (let [{:keys [title contents]} @page] 
        [:section.content-wrapper
          [:div.content
            [:button.edit-button 
             {:on-click #(swap! editing not)} 
             (if-not @editing "Edit" "Done")]
            [:article#page
              [:h1.post-title title]
              [:article (markdown-content contents)]]]
            [editor {:page page :editing @editing}]]))))

(defn wiki-page []
  (let [page (subscribe [:current-page])]
    (fn []
      [:div
       (layout-header)
        [wiki-page-contents page]])))

(defn settings-page []
  (let [linked-with-dropbox? (subscribe [:linked-with-dropbox?])]
    (fn []
        [:div
        (layout-header)
        [:section.content-wrapper
        [:div.content
        [:article#page
            [:h1.post-title "Settings"]
            [:p "These are your settings."]
            [:h2 "Sync"]
            [:button
             {:on-click #(dispatch [:linked-with-dropbox (not @linked-with-dropbox?)])}
             (if @linked-with-dropbox? "Unlink Dropbox" "Link with Dropbox")]]]]])))

(defn page-list-item [page]
  (let [title (:title page)
        contents (:contents page)
        preview (->> (string/split contents #" ")
                     (take 10)
                     (string/join " "))
        permalink (:permalink page)
        date-format (f/formatter "MMMM d, yyyy")
        date (f/unparse date-format (coerce/from-date (:timestamp page)))
        path (str "/page/" permalink)]
    [:li
     [:a.page-link {:href path} title]
     [:span.page-date date]
     [:p.page-preview (str preview "...")]]))

(defn search-page []
  (let [pages (subscribe [:all-pages])]
    (fn []
      [:div
      (layout-header)
      [:section.content-wrapper
      [:div.content
        [:article#page
          [:h1.post-title "Search"]
          [:ul.page-list
            (map page-list-item @pages)]]]]])))

(defn home-page []
  [:div
   (layout-header)
   [:section.content-wrapper
    [:div.content
        [:article#page 
            [:h1.post-title ""]
            [:p ""]]]]])

(defn about-page []
  [:div [:h1 "This is an about page"]
   [:div [:a {:href "/"} "Home Page"]]])

;; -------------------------
;; Routes
(register-sub :current-route 
  (fn [db] 
    (reaction (get-in @db [:current-route]))))

;(def page-navigate-chan (chan))

;(defn notify-page-change [{[route-name args] :current-route} db]
  ;(when (= route-name :wiki-page-view)
    ;(put! page-navigate-chan (:page args))))

(secretary/set-config! :prefix "/")

(secretary/defroute "/" []
  (dispatch [:navigate [:home-page {}]]))

(secretary/defroute "/settings" []
  (dispatch [:navigate [:settings-page {}]]))

(secretary/defroute "/search" []
  (go
    (let [pages (<! (page-db/load-all!))]
      (dispatch [:navigate [:search-page {:pages pages}]]))))

(secretary/defroute "/about" []
  (dispatch [:navigate [:about-page {}]]))

(secretary/defroute "/page/:page-permalink" [page-permalink]
  (go
    (let [maybe-page (<! (page-db/load page-permalink))
          page (if (= maybe-page :not-found) 
                 (page/new-page page-permalink) 
                 maybe-page)
          html-contents (markdown->html (:contents page))
          ;; Preload any internal images, so that they can be synchronously displayed whenever the page re-renders
          ;; This circumvents a frustrating issue where, when typing, the page content gets continually reloaded
          ;; and images flash from broken to fixed as they are asynchronously reloaded.
          with-links (<! (load-images html-contents))]
      (dispatch [:navigate [:wiki-page-view {:page page}]]))))

;; -------------------------
;; History
;; must be called after routes have been defined
;; -------------------------
;; Initialize app

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

(def history (pushy/pushy secretary/dispatch!
  (fn [x] (when (secretary/locate-route x) x))))

(defn init! []
  (go
    (let [cursor (storage/load "cursor")
          linked-with-dropbox? (storage/load "linked-with-dropbox")]
      (dispatch-sync [:initialize {:cursor cursor
                                   :linked-with-dropbox? linked-with-dropbox?}])
      (pushy/start! history)
      (render)

      (when (<! (sync/connect))
        (swap! app-db assoc :linked-with-dropbox? true)

        (sync/start-polling (:cursor @app-db))))))
