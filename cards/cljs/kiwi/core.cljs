(ns cards.kiwi.core
  (:require
   [devcards.core :refer-macros [defcard defcard-rg]]
   [reagent.core :as reagent]
   [kiwi.core]))

(defonce contents (reagent/atom "# First heading\n\n## Second heading\n\nThis is editor contents."))

(def prosemirror-view (js/require "prosemirror-view"))
(def EditorView (.-EditorView prosemirror-view))

(def prosemirror-state (js/require "prosemirror-state"))
(def EditorState (.-EditorState prosemirror-state))

(def prosemirror-schema (js/require "prosemirror-schema-basic"))
(def schema (.-schema prosemirror-schema))

(def prosemirror-example-setup (js/require "prosemirror-example-setup"))
(def exampleSetup (.-exampleSetup prosemirror-example-setup))

(defn prose-editor []
  (let [state (atom {})]
    (reagent/create-class
     {:should-component-update #(false)
      :component-did-mount
      (fn [this]
        (let [{:keys [editor-state on-editor-state-change]} (reagent/props this)
              node (reagent/dom-node this)
              dispatch-tx (fn [tx]
                            (let [new-state (editor-state.apply tx)
                                  editor (:editor @state)]
                              (editor.updateState new-state)
                              (on-editor-state-change new-state)))
              editor (EditorView. node (clj->js {:state editor-state
                                                 :dispatch-transaction dispatch-tx}))]
          (swap! state assoc :editor editor)))
      :component-will-unmount
      (fn [this]
        (let [editor (:editor @state)]
          (.destroy editor)))
      :component-will-receive-props
      (fn [this new-argv]
        (let [editor (:editor @state)
              next-props (reagent.impl.component/extract-props new-argv)]
          (editor.updateState (.-editor-state next-props)))
        )
      :reagent-render
      (fn []
        [:div])})))

(defonce text-editor-state
  (reagent/atom
   {:editor-state
    (EditorState.create (clj->js {:schema schema
                                  :plugins (exampleSetup (clj->js {:schema schema}))}))}))

(defn text-editor []
  (fn []
    (let [editor-state (:editor-state @text-editor-state)
          on-editor-state-change #(swap! text-editor-state assoc :editor-state %)]
      [prose-editor {:editor-state editor-state
                     :on-editor-state-change on-editor-state-change}])))


(defcard-rg prosemirror-editor
  "Testing out prosemirror. 

   **TODO:** upon hot-reload the editor state is cleared. Not sure if this is because of 
   the way I've wrapped Prosemirror / react, or a misuse of devcards."
  [:div
   [text-editor]])

(defcard-rg page-editor
  "This is the editor used to edit wiki pages."
  (let [page {:title "Test page"}]
    [:div
     [kiwi.editor.views/editor* {:page page
                                 :contents @contents
                                 :on-change #()}]]))

(devcards.core/start-devcard-ui!)
