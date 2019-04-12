(ns kiwi.editor.views
  (:require 
   [re-frame.core :as re-frame :refer [after dispatch dispatch-sync enrich path register-handler register-sub subscribe]]
   [kiwi.db :as db]
   [reagent.core :as reagent]
   [clojure.string :as string]))

(def fs (js/require "fs"))

(defn- page-title-field [page]
  (let [{:keys [title]} page]
    [:h1.post-title title]))

(defn drag-enter [e local-state]
  (let [items (.-dataTransfer.items e)]
    (.preventDefault e)
    (.stopPropagation e)
    (swap! local-state update-in [:counter] inc)
    (when (and (not (nil? items))
               (not (= 0 (.-length items))))
      (swap! local-state assoc :dragging true))))

(defn drag-leave [e local-state]
  (.preventDefault e)
  (.stopPropagation e)
  (swap! local-state update-in [:counter] dec)
  (when (= 0 (:counter @local-state))
    (swap! local-state assoc :dragging false)))

(defn drag-over [e local-state])

(defn as-img-path [file]
  "img/" (.-name file))

(defn copy-image-to-wiki! [file img-dir]
  (fs.copyFileSync (.-path file) (str img-dir (.-name file))))

(defn on-drop [e local-state editor img-dir]
  (let [items (.-dataTransfer.files e)
        eventCoords {:left (.-pageX e)
                     :top (.-pageY e)}
        coords (.coordsChar editor
                            (clj->js eventCoords))]
    (when (and (not (nil? items))
               (not (= 0 (.-length items))))
      (let [file (aget items 0)]
        (when (string/starts-with? (.-type file) "image")
          (.preventDefault e)
          (.stopPropagation e)

          (copy-image-to-wiki! file img-dir)

          (.setCursor editor coords)
          (.replaceRange editor
                         (str "![](" (as-img-path file) ")")
                         coords))))
    (swap! local-state assoc :counter 0)
    (swap! local-state assoc :dragging false)))

;; Adds syntax highlighting for [[Internal Links]]
(defn token-fn [^js/CodeMirror.StringStream stream state] 
  (if-let [match (when (.match stream "[[")
                   (loop [ch (.next stream)]
                     (if (and (= ch "]")
                              (= (.next stream) "]"))
                       (do (.eat stream "]")
                           "internal-link")
                       (when-not (nil? ch)
                         (recur (.next stream))))))]
    match
    (do
      (while (and (.next stream) 
                  (not (.match stream "[[" false))
                  (not (.match stream "\n" false)))
        1))))

(defn define-kiwi-mode! []
  (let [mode-name "kiwi"]
    (.defineMode
     js/CodeMirror
     mode-name
     (fn [config, parser-config]
       (.overlayMode js/CodeMirror
                     (.getMode js/CodeMirror 
                               config 
                               (or (.-backdrop parser-config) "gfm"))
                     #js {:token token-fn})))
    mode-name))

(defn editor-options [contents mode-name]
  {:value contents
   :mode mode-name
   :theme "default"
   :autofocus true
   :dragDrop true
   :viewportMargin js/Infinity
   :lineWrapping true
   :extraKeys {:Tab (fn [^js/CodeMirror cm]
                      (if (.somethingSelected cm)
                        (.indentSelection cm "add")
                        (.replaceSelection
                         cm
                         (-> cm
                             (.getOption "indentUnit")
                             (+ 1)
                             js/Array
                             (.join " "))
                         "end"
                         "+input")))}})


(defn- page-content-field []
  (let [local-state (reagent/atom {})
        wiki-root-dir @(subscribe [:wiki-root-dir])
        img-dir (str wiki-root-dir "/" db/img-rel-path)]
    (reagent/create-class
      {:reagent-render 
       (fn []
         [:textarea 
          {:style {:width "100%" :height "500px" :display "none"}}])
       :component-did-mount 
       (fn [this]
         (let [{:keys [on-change contents]} (reagent/props this)
               node (reagent/dom-node this)
               mode-name (define-kiwi-mode!)
               editor (js/CodeMirror #(.appendChild (.-parentNode node) %)
                                     (clj->js (editor-options contents mode-name)))]

           (swap! local-state assoc :editor editor)
           (swap! local-state assoc :value contents)

           (.on editor "drop"
                (fn [^js/CodeMirror instance e]
                  (on-drop e local-state instance img-dir)))
           (.on editor "dragenter"
                (fn [^js/CodeMirror instance e]
                  (drag-enter e local-state)))
           (.on editor "dragleave"
                (fn [^js/CodeMirror instance e]
                  (drag-leave e local-state)))
           (.on editor "dragover"
                (fn [^js/CodeMirror instance e]
                  (drag-over e local-state)))

           (.on editor "change" 
                (fn [^js/CodeMirror instance change-obj]
                  (let [new-value (.getValue instance)]
                    (swap! local-state assoc :value new-value)
                    (on-change new-value))))))})))

(defn editor* [{:keys [page contents on-change]}]
  [:div#edit-container 
   [:div#edit
    [page-title-field page]
    [page-content-field {:contents contents
                         :on-change on-change}]]])

(defn editor [{:keys [page editing]}]
  (let [contents (subscribe [:edited-contents])
        on-change #(dispatch-sync [:edit-page %])]
    (fn [{:keys [page editing]}]
      [editor* {:page page
                :contents @contents
                :on-change on-change}])))
