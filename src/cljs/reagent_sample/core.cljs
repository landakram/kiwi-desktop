(ns reagent-sample.core
    (:require-macros [reagent.ratom :refer [reaction]]
                     [cljs.core.async.macros :refer [go-loop]])
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [re-frame.db :refer [app-db]]
              [re-frame.core :as re-frame :refer [dispatch dispatch-sync register-sub subscribe register-handler after path]]
              [secretary.core :as secretary :include-macros true]
              [goog.events :as events]
              [pushy.core :as pushy]
              [reagent-sample.sync :as sync]
              [clojure.string :as string]
              [goog.history.EventType :as EventType]
              [tailrecursion.cljson :refer [clj->cljson cljson->clj]]
              [cljs.core.async :refer [chan <! put!]]
              [reagent-sample.storage :as storage]
              [reagent-sample.page :as page]))

;; -------------------------
;; Views

(defonce initial-state {:current-route [:home-page {}]})

(register-sub :current-page
  (fn [db] (reaction (get-in @db [:current-route 1 :page]))))

(register-handler :initialize
  (fn [db [_ state]]
    ; Use initial-state as a default, but keep anything already in db
    (merge initial-state db (or state {}))))

(dispatch-sync [:initialize {}])

(defonce page-data
  (atom
    {:title ""
     :contents ""}))

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
         {:__html (-> content str js/marked page/parse-wiki-links)}}])
     {:component-did-mount
      (fn [this]
        (let [node (reagent/dom-node this)]
          (highlight-code node)))})])

(defn page-title-field [page]
  (let [{:keys [title]} @page]
    [:input
     {:type "text"
      :value title
      :on-change #(do (swap! page-data assoc :title (-> % .-target .-value))
                      (storage/save! (page/get-permalink @page-data) @page-data))}]))

(def page-chan (chan))

; Save updated pages to localStorage
(go-loop []
  (let [page (<! page-chan)]
    (storage/save! (page/get-permalink page) page)
    (recur)))

(defn put-page-chan [page] 
  (put! page-chan page))

(defn persist-page [page] (storage/save! (page/get-permalink page) page))

(register-handler :page-edit 
  ; This vector is middleware. 
  ; The first one scopes app-db to the :page, so that the handler function below 
  ; it receives page instead of the full app-db.
  ; The second one puts the updated page on a chan, where subscribers can
  ; listen to it. This is used to save to localStorage / sync to dropbox.
  [(path :current-route 1 :page) (after put-page-chan)]
  (fn [page [_ contents]]
    (assoc-in page [:contents] contents)))

(def textarea->code-mirror-chan (chan))

(defn page-content-textarea [page]
  (let [{:keys [contents]} @page]
    (put! textarea->code-mirror-chan contents)
    [:textarea
     {:style {:width "100%" :height "500px" :display "none"}
      :value contents}]))

(def page-content-field 
  (with-meta page-content-textarea
    ; Turn the textarea into a CodeMirror editor window
    {:component-did-mount
     (fn [this]
       (let [node (reagent/dom-node this)
             editor (js/CodeMirror #(.appendChild (.-parentNode node) %)
                                   (clj->js
                                     {:value (.-value node)
                                      :mode "gfm"
                                      :theme "default"
                                      :viewportMargin js/Infinity
                                      :lineWrapping true}))]
         (.on editor "change" #(dispatch [:page-edit (-> % .getValue)]))
         ; This is pretty icky. This loop exists to update CodeMirror when
         ; the wiki page changes. Without this, the `editor` never updates 
         ; when contents get updated w/o being explicitly being typed here.
         (go-loop []
           (let [changes (<! textarea->code-mirror-chan)]
             (when-not (= changes (.getValue editor))
                (.setValue editor changes))
             (recur)))))}))

(defn wiki-page-contents [page]
  (let [{:keys [title contents]} @page] 
    [:section#content
     [:article#page
      [:h1 title]
      [:article [markdown-content contents]]]
     [page-title-field page]
     [page-content-field page]]))

(defn wiki-page []
  (let [page (subscribe [:current-page])]
    (fn []
      [:div
        [wiki-page-contents page]])))

(defn home-page []
  [:div 
   [:section#content
    [:article#page 
      [:h1 "Home"]
      [:p "This is just some stuff"]]]])

(defn about-page []
  [:div [:h1 "This is an about page"]
   [:div [:a {:href "/"} "Home Page"]]])

;; -------------------------
;; Routes
(register-sub :current-route 
  (fn [db] 
    (reaction (get-in @db [:current-route]))))

(register-handler :navigate 
  (fn [db [_ route]]
    (assoc db :current-route route)))

(secretary/set-config! :prefix "/")

(secretary/defroute "/" []
  (dispatch [:navigate [:home-page {}]]))

(secretary/defroute "/about" []
  (dispatch [:navigate [:about-page {}]]))

(secretary/defroute "/page/:page-permalink" [page-permalink]
  (let [page (storage/load page-permalink)]
    (dispatch [:navigate [:wiki-page-view {:page page}]])))

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

(defn app []
  (let [current-route (subscribe [:current-route])]
    (apply page @current-route)))

(defn render [] 
  (reagent/render [app] (.getElementById js/document "app")))

(def history (pushy/pushy secretary/dispatch!
  (fn [x] (when (secretary/locate-route x) x))))

;(add-watch app-db :watch-current-page
  ;(fn [key atom old-val new-val]
    ;(println new-val)))

(defn init! []
  (pushy/start! history)
  (render)
  )

