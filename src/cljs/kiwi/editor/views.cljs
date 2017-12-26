(ns kiwi.editor.views
  (:require 
   [re-frame.core :as re-frame :refer [after dispatch dispatch-sync enrich path register-handler register-sub subscribe]]
   [reagent.core :as reagent]))

(defn- page-title-field [page]
  (let [{:keys [title]} @page]
    [:h1.post-title title]))

(defn- page-content-field []
  (let [local-state (atom {})]
    (reagent/create-class
      {:reagent-render 
       (fn []
         [:textarea 
          {:style {:width "100%" :height "500px" :display "none"}}])
       :component-did-mount 
       (fn [this]
         (print "editor-did-mount")
         (let [{:keys [on-change contents]} (reagent/props this)
               node (reagent/dom-node this)
               ;; Adds syntax highlighting for [[Internal Links]]
               token-fn (fn [^js/CodeMirror.StringStream stream state] 
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
                                                             "+input")))}
                                       }))]
           (swap! local-state assoc :editor editor)
           (swap! local-state assoc :value contents)
           (.on editor "change" 
                (fn [^js/CodeMirror instance change-obj]
                  (let [new-value (.getValue instance)]
                    (swap! local-state assoc :value new-value)
                    (on-change new-value))))))})))


(defn editor [{:keys [page editing]}]
  [:div#edit-container 
    [:div#edit
     [page-title-field page]
     [page-content-field {:contents @(subscribe [:edited-contents])
                          :on-change #(dispatch-sync [:edit-page %])}]]])
