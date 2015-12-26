(ns reagent-sample.core
    (:require-macros [reagent.ratom :refer [reaction]]
                     [cljs.core.async.macros :refer [go-loop]])
    (:require [reagent.core :as reagent]
              [reagent.session :as session]
              [re-frame.db :refer [app-db]]
              [re-frame.core :as re-frame :refer [dispatch dispatch-sync register-sub subscribe register-handler after enrich path]]
              [secretary.core :as secretary :include-macros true]
              [goog.events :as events]
              [pushy.core :as pushy]
              [reagent-sample.sync :as sync]
              [clojure.string :as string]
              [goog.history.EventType :as EventType]
              [tailrecursion.cljson :refer [clj->cljson cljson->clj]]
              [cljs.core.async :refer [chan <! put! pipe timeout]]
              [reagent-sample.storage :as storage]
              [reagent-sample.utils :as utils]
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
    [:h1 title]))

(defn debounce [in ms]
  (let [out (chan)]
    (go-loop [last-val nil]
      (let [val (if (nil? last-val) (<! in) last-val)
            timer (timeout ms)
            [new-val ch] (alts! [in timer])]
        (condp = ch
          timer (do (>! out val) (recur nil))
          in (recur new-val))))
    out))

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

(def page-chan (chan))
(def page-changes (debounce page-chan 1000))

;(def page-changes  
  ;(watch-in-chan app-db [:current-route 1 :page]))

;(go-loop []
  ;(let [page (<! page-changes)]
    ;(recur)))

; Save updated pages to localStorage
(go-loop []
  (let [page (<! page-changes)]
    (storage/save! (page/get-permalink page) page)
    (when (:dirty? page)
        (sync/write! page))
    (recur)))

(defn put-page-chan [page] 
  (put! page-chan page))

(register-handler :page-edit 
  ; This vector is middleware. 
  ; The first one scopes app-db to the :page, so that the handler function below 
  ; it receives page instead of the full app-db.
  ; The second one puts the updated page on a chan, where subscribers can
  ; listen to it. This is used to save to localStorage / sync to dropbox.
  [(path :current-route 1 :page) (after put-page-chan)]
  (fn [page [_ contents]]
    (-> page
      (assoc-in [:dirty?] true)
      (assoc-in [:contents] contents)
      (assoc-in [:timestamp] (js/Date)))))

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
              editor (js/CodeMirror #(.appendChild (.-parentNode node) %)
                                    (clj->js
                                      {:value contents
                                        :mode "gfm"
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
            (.setValue (.-doc (:editor @local-state)) contents))
          ))})))

(defn editor [{:keys [page editing]}]
  (if-not editing 
    [:div] 
    [:div#edit-container 
     [:div#edit
      [page-title-field page]
      [page-content-field {:contents (:contents @page) 
                           :on-change #(dispatch-sync [:page-edit %])}]]]))

(defn layout-header []
  [:div.header])

(defn wiki-page-contents [page]
  (let [editing (reagent/atom false)]
    (fn [page]
      (let [{:keys [title contents]} @page] 
        [:section.content-wrapper
          [:div.content
            [:button.edit-button 
             {:on-click #(swap! editing not)} 
             (if-not @editing "edit" "done")]
            [:article#page
              [:h1 title]
              [:article [markdown-content contents]]]]
            [editor {:page page :editing @editing}]]))))

(defn wiki-page []
  (let [page (subscribe [:current-page])]
    (fn []
      [:div
       (layout-header)
        [wiki-page-contents page]])))

(defn home-page []
    [:div.content
        [:article#page 
            [:h1 "Home"]
            [:p "This is just some stuff"]]])

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

; Ugly, but checks whether the currently displayed wiki page is the page
(defn is-current-page [page [route-name route-args]]
    (and (= :wiki-page-view route-name) 
         (= (get-in route-args [:page :title]) (:title page))))

; Kind of a weird use of re-frame's handlers but:
;
; Here, I use the enrich middleware to *save whichever assoc'd page to localStorage*.
; Then, if the assoc'd page is currently being displayed, I update app-db.
; (app-db only contains the currently viewed page.)
(register-handler :assoc-page
  [(enrich (fn [db [_ page]] 
               (storage/save! (page/get-permalink page) page)
               db))]
  (fn [db [_ page]]
    (if (is-current-page page (:current-route db))
        (assoc-in db [:current-route 1 :page] page)
        db)))

(register-handler :pull-notes
  [ (after #(storage/save! "cursor" (:cursor %)))]
  (fn [db [_ pull-results]]
    (assoc db :cursor (:cursor pull-results))))

(register-handler :navigate 
  ;[(after notify-page-change)]
  (fn [db [_ route]]
    (assoc db :current-route route)))

(secretary/set-config! :prefix "/")

(secretary/defroute "/" []
  (dispatch [:navigate [:home-page {}]]))

(secretary/defroute "/about" []
  (dispatch [:navigate [:about-page {}]]))

(secretary/defroute "/page/:page-permalink" [page-permalink]
  (let [title (page/get-title-from-permalink page-permalink)
        page (or (storage/load page-permalink) (page/new-page page-permalink))]
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
  (dispatch-sync [:initialize {:cursor (storage/load "cursor")}])
  (pushy/start! history)
  (render)
  (sync/start-polling (:cursor @app-db)))



