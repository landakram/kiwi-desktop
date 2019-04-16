(ns cards.kiwi.core
  (:require
   [oops.core :refer [oset! oset!+]]
   [devcards.core :refer-macros [defcard defcard-rg defcard-doc]]
   [reagent.core :as reagent]
   [kiwi.core]))

(defonce contents (reagent/atom "# First heading\n\n## Second heading\n\nThis is editor contents."))

(def prosemirror-view (js/require "prosemirror-view"))
(def EditorView (.-EditorView prosemirror-view))

(def prosemirror-state (js/require "prosemirror-state"))
(def EditorState (.-EditorState prosemirror-state))

(def prosemirror-example-setup (js/require "prosemirror-example-setup"))
(def exampleSetup (.-exampleSetup prosemirror-example-setup))

(def prosemirror-model (js/require "prosemirror-model"))
(def Schema (.-Schema prosemirror-model))

(defn compile-schema [schema]
  (let [js-schema #js {:nodes #js {}
                       :marks #js {}}
        nodes (partition 2 (:nodes schema))
        marks (partition 2 (:marks schema))]

    (doseq [[key value] nodes]
      (oset!+ js-schema (str "!nodes.!" (name key)) (clj->js value)))

    (doseq [[key value] marks]
      (oset!+ js-schema (str "!marks.!" (name key)) (clj->js value)))

    js-schema))

(def schema*
  {:nodes
   [:doc {:content "block+"},

    :paragraph
    {:content "inline*",
     :group "block",
     :parseDOM [{:tag "p"}]
     :toDOM (fn [] (clj->js ["p" 0]))},

    :blockquote
    {:content "block+",
     :group "block",
     :parseDOM [{:tag "blockquote"}]
     :toDOM (fn [] (clj->js ["blockquote" 0]))},

    :horizontal_rule
    {:group "block",
     :parseDOM [{:tag "hr"}]
     :toDOM (fn [] (clj->js ["div" ["hr"]]))},

    :heading
    {:attrs {:level {:default 1}},
     :content "inline*",
     :group "block",
     :defining true,
     :parseDOM
     [{:tag "h1", :attrs {:level 1}}
      {:tag "h2", :attrs {:level 2}}
      {:tag "h3", :attrs {:level 3}}
      {:tag "h4", :attrs {:level 4}}
      {:tag "h5", :attrs {:level 5}}
      {:tag "h6", :attrs {:level 6}}]
     :toDOM (fn [node] (clj->js [(str "h" (.-level (.-attrs node))) 0]))},

    :code_block
    {:content "text*",
     :group "block",
     :code true,
     :marks ""
     :defining true,
     :attrs {:params {:default ""}},
     :parseDOM [{:tag "pre",
                 :preserveWhitespace "full",
                 :getAttrs (fn [node]
                             (clj->js
                              {:params (or (.getAttribute node "data-params")
                                           "")}))}]
     :toDOM (fn [node]
              (let [params (.-params (.-attrs node))]
                (clj->js
                 ["pre"
                  (if params params {})
                  ["code" 0]])))},

    :ordered_list
    {:content "list_item+",
     :group "block",
     :attrs {:order {:default 1}, :tight {:default false}},
     :parseDOM [{:tag "ol"
                 :getAttrs (fn [node]
                             (clj->js
                              {:order (if (.hasAttribute node "start")
                                        (.getAttribute node "start")
                                        1)
                               :tight (.hasAttribute node "data-tight")}))}]
     :toDOM (fn [node]
              (let [order (.-order (.-attrs node))]
                (clj->js
                 ["ol" {:start (if (= order 1) nil order)
                        :data-tight (if (.-tight (.-attrs node)) "true" nil)}
                  0])))},

    :bullet_list
    {:content "list_item+",
     :group "block",
     :attrs {:tight {:default false}},
     :parseDOM [{:tag "ul"
                 :getAttrs (fn [node]
                             (clj->js
                              {:tight (.hasAttribute node "data-tight")}))}]
     :toDOM (fn [node]
              (clj->js
               ["ul"
                {:data-tight (if (.-tight (.-attrs node))
                               "true"
                               nil)}
                0]))},

    :list_item
    {:content "paragraph block*",
     :defining true,
     :parseDOM [{:tag "li"}]
     :toDOM (fn []
              (clj->js
               ["li" 0]))},

    :text
    {:group "inline"}

    :image
    {:inline true,
     :attrs {:src {}, :alt {:default nil}, :title {:default nil}},
     :group "inline",
     :draggable true,
     :parseDOM [{:tag "img[src]"
                 :getAttrs (fn [node]
                             (clj->js
                              {:src (.getAttribute node "src")
                               :title (.getAttribute node "title")
                               :alt (.getAttribute node "alt")}))}]
     :toDOM (fn [node] (clj->js ["img" (.-attrs node)]))},


    :hard_break
    {:inline true,
     :group "inline",
     :selectable false,
     :parseDOM [{:tag "br"}]
     :toDOM (fn [] (clj->js ["br"]))}]

   :marks
   [:em
    {:parseDOM [{:tag "i"} {:tag "em"} {:style "font-style"
                                        :getAttrs (fn [value]
                                                    (clj->js
                                                     (and
                                                      (= value "italic")
                                                      nil)))}]
     :toDOM (fn [] (clj->js ["em"]))},

    :strong
    {:parseDOM [{:tag "b"} {:tag "strong"} {:style "font-weight"
                                            :getAttrs (fn [value]
                                                        (clj->js
                                                         (and
                                                          (re-find
                                                           #"^(bold(er)?|[5-9]\d{2,})$"
                                                           value)
                                                          nil)))}]
     :toDOM (fn [] (clj->js ["strong"]))},

    :link
    {:attrs {:href {}, :title {:default nil}},
     :inclusive false,
     :parseDOM [{:tag "a[href]"
                 :getAttrs (fn [node]
                             (clj->js
                              {:href (.getAttribute node "href")
                               :title (.getAttribute node "title")}))}]
     :toDOM (fn [node]
              (clj->js ["a" (.-attrs node)]))}, 

    :code
    {:parseDOM [{:tag "code"}]
     :toDOM (fn [] (clj->js ["code"]))}]})

(def schema (Schema. (compile-schema schema*)))

(defn prose-editor []
  (let [state (atom {})]
    (reagent/create-class
     {:should-component-update (fn [this] false)
      :component-did-mount
      (fn [this]
        (let [{:keys [editor-state on-editor-state-change]} (reagent/props this)
              node (reagent/dom-node this)
              dispatch-tx (fn [tx]
                            (let [{:keys [editor-state]} (reagent/props this)
                                  new-state (.apply editor-state tx)
                                  editor (:editor @state)]
                              (print "dispatch-tx")
                              ;; We let component-will-receive-props actually update editor-state
                              (on-editor-state-change new-state)))
              editor (EditorView. node (clj->js {:state editor-state
                                                 :dispatchTransaction dispatch-tx}))]
          (print "component-did-mount")
          (swap! state assoc :editor editor)))
      :component-will-unmount
      (fn [this]
        (let [editor (:editor @state)]
          (.destroy editor)))
      :component-will-receive-props
      (fn [this new-argv]
        (let [editor (:editor @state)
              {:keys [editor-state]} (reagent/props this)
              next-props (reagent.impl.component/extract-props new-argv)
              new-editor-state (:editor-state next-props)]
          (print "component-will-receive-props")
          (when (not (= editor-state new-editor-state))
            (print "updating state")
            (.updateState editor (:editor-state next-props))))
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
          on-editor-state-change (fn [state]
                                   (print "on-editor-state-change")
                                   (swap! text-editor-state assoc :editor-state state))]
      [prose-editor {:editor-state editor-state
                     :on-editor-state-change on-editor-state-change}])))


(defcard-doc
  "### ProseMirror

ProseMirror is a editor framework for building WYSIWYG editors.

I want to see if I can replace CodeMirror with a custom ProseMirror editor, as 
I'd like to be able to edit the wiki directly without needing to switch between
view and edit.")

(defcard-rg prosemirror-editor
  "Here's an editor using ProseMirror.

   Note that a `kiwi-schema` namespace is exposed from JavaScript that is compiled by webpack. 
   (It's convenient to run `npx webpack -w` while iterating on JavaScript stuff.)
   
   We previusly defined the schema in JavaScript because ProseMirror relies on the ordering 
   of the schema. Clojure maps are unordered (and actually re-order keys), so when we try 
   to define the schema as a Clojure map, unexpected nodes in the schema get precedent.

   But as of `805cd11`, we just define the schema in cljs as an array and then 
   write a custom function that builds the js object from that."

  [:div
   [text-editor]])

(defcard-doc
  "### Interop between ProseMirror and mdast

   Kiwi currently uses [remark](https://github.com/remarkjs/remark) and 
   [mdast](https://github.com/syntax-tree/mdast#nodes) to parse, manipulate, and extend markdown.

   For example, task-list checkboxes are implemented as a remark plugin, 
   [remark-task-list](https://github.com/landakram/remark-task-list). 

   If I want to support using either style of editor in settings, then I might as well
   make it so the schemas can adapt to each other.

   Doing this isn't strictly necessary... I could just go straight to/from markdown. But remark
   already parses markdown into a known, high-level AST that I like. 

   It's also an interesting exercise.
")

(defcard mdast-schema-example
  "Here's an example of an AST of `*Hi*` using mdast:"
  (kiwi.markdown-processors/get-ast "*Hi*"))

(defcard-doc
  "I want to provide bi-directional translation between schemas.

   To start, we fully enumerating all node types: 

   **TODO**")

(defcard-doc
  "Other useful links: 

* [prosemirror-markdown md -> schema](https://github.com/ProseMirror/prosemirror-markdown/blob/master/src/from_markdown.js)
* [ProseMirror schema docs](https://prosemirror.net/docs/guide/#schema). Note that documents are built using the 
  schema object (i.e. `schema.node(\"doc\"...)`)")

(defcard-doc
  "### CodeMirror

   For comparison, here is the current editor.
")

(defcard-rg page-editor
  "This is the editor used to edit wiki pages."
  (let [page {:title "Test page"}]
    [:div
     [kiwi.editor.views/editor* {:page page
                                 :contents @contents
                                 :on-change #()}]]))

(devcards.core/start-devcard-ui!)
