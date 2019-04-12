(ns cards.kiwi.core
  (:require
   [devcards.core :refer-macros [defcard defcard-rg]]
   [reagent.core :as reagent]
   [kiwi.core]))

(defonce contents (reagent/atom "# First heading\n\n## Second heading\n\nThis is editor contents."))

(defcard-rg page-editor
  "This is the editor used to edit wiki pages."
  (let [page {:title "Test page"}]
    [:div
     [kiwi.editor.views/editor* {:page page
                                 :contents @contents
                                 :on-change #()}]]))

(devcards.core/start-devcard-ui!)
