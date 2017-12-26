(ns kiwi.page.views
  (:require [kiwi.views :as views]
            [reagent.core :as reagent]
            [kiwi.page.markdown :as markdown]
            [re-com.core :as re-com]
            [clojure.string :as string]
            [re-frame.core :as re-frame :refer [after dispatch dispatch-sync enrich path register-handler register-sub subscribe]]
            [kiwi.page.core :as page]
            [kiwi.db :as page-db]
            [kiwi.features :as features]
            [kiwi.routes :as routes])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))

(defn- markdown-content [content]
  (let [wiki-root-dir (subscribe [:wiki-root-dir])
        permalinks (subscribe [:permalinks])]
    (reagent/create-class
      {:reagent-render 
       (fn [content]
         [:div
          {:dangerouslySetInnerHTML
           {:__html (markdown/markdown->html @wiki-root-dir content @permalinks)}}])
       :component-did-update
       (fn [this]
         (let [node (reagent/dom-node this)]
           (js/window.renderMath)
           (markdown/attach-checkbox-handlers node)
           (markdown/highlight-code node)))
       :component-did-mount
       (fn [this]
         (let [node (reagent/dom-node this)]
           (js/window.renderMath)
           (markdown/attach-checkbox-handlers node)
           (markdown/highlight-code node)))})))

(defn- delete-button [page editing]
  (fn [page]
    [:button.btn.btn-danger {:on-click
                             (fn [e]
                               (dispatch [:show-modal :delete-page]))}
     [:i.fa.fa-trash]
     " Delete"]))

(defn- schedule-button [js-start-date]
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

(defn- edit-button [editing]
  [:button.edit-button.btn.btn-default
   {:on-click (fn [] (dispatch [:assoc-editing?  (not @editing)]))} 
   (if-not @editing
     [:i.fa.fa-pencil]
     [:i.fa.fa-check])
   (if-not @editing
     " Edit"
     " Done")])

(defn- wiki-page-contents [page]
  (let [editing (subscribe [:editing?])]
    (fn [page]
      (let [{:keys [title contents tags]} @page] 
        [:div
         [:div.btn-group.pull-right
          (when features/schedule-enabled?
            (js/console.log page)
            [schedule-button (get @page :scheduled)])
          (when @editing
            [delete-button page editing])
          [edit-button editing]]
         (if-not @editing
           [:article#page
            [:h1.post-title title]
            [views/tags-list tags]
            [:article [markdown-content contents]]]
           [kiwi.editor.views.editor {:page page :editing @editing}])]))))

(defn wiki-page []
  (let [page (subscribe [:current-page])]
    (reagent/create-class
     {:reagent-render 
      (fn []
        [views/base-layout
         [wiki-page-contents page]])})))
