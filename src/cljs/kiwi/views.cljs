(ns kiwi.views
  (:require 
   [re-frame.core :as re-frame :refer [after dispatch dispatch-sync enrich path register-handler register-sub subscribe]]
   [kiwi.routes :as routes]
   [reagent.core :as reagent]
   [re-com.core :as re-com]
   [clojure.string :as string]
   [kiwi.page :as page]
   [kiwi.db :as page-db])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]
   [reagent.ratom :refer [reaction]]))

(defn- dispatch-new-page! []
  (dispatch [:show-modal :add-page]))

(defn- layout-header []
  [:div.header
   [:div [:a.btn.btn-default {:href (routes/page-route {:permalink "home"})} [:i.fa.fa-home] " Home"]]
   [:nav.navigation
    [:div.btn-group
     [:a.btn.btn-default {:href (routes/settings-route)} [:i.fa.fa-cog] " Settings"]
     [:a.btn.btn-default {:href (routes/search-route)} [:i.fa.fa-search] " Search"]
     [:button.btn.btn-default
      {:on-click dispatch-new-page!}
      [:i.fa.fa-plus] " New page"]]]])


(defonce _modals (atom {}))

(defn modals [id]
  (get @_modals id))

(defn def-modal [id view]
  (swap! _modals assoc id view))

;;; TODO everything in here to their own components

(def sugar (js/require "sugar-date"))

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
                   :attr {:auto-focus true}
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



(defn schedule-page-form
  "A form that lets a user specify a date and time to schedule a page"
  []
  (let [page (subscribe [:current-page])
        date-string (reagent/atom "")]
    (when (get @page :scheduled)
      (reset! date-string
              (-> (get @page :scheduled)
                  (sugar.Date.)
                  (.full)
                  (.-raw)))
      )
    (fn []
      [re-com/v-box
       :gap "10px"
       :width "400px"
       :children [[:h3 "Schedule page"]
                  [:p "Scheduling a page will add it as an event to Google Calendar."]
                  [re-com/input-text
                   :model date-string
                   :width "auto"

                   :on-change #(reset! date-string %)
                   :attr {:auto-focus true}
                   :change-on-blur? false
                   :placeholder "3pm tomorrow"]
                  [re-com/label
                   :label
                   (when-not (string/blank? @date-string)
                     [:p [:i (str (sugar.Date.create @date-string))]])]
                  [re-com/button
                   :label "Schedule"
                   :class "btn-success"
                   :disabled? (= (str (sugar.Date.create @date-string)) "Invalid Date")
                   :on-click (fn [_]
                               (re-frame/dispatch [:schedule-page @page (sugar.Date.create @date-string)])
                               (re-frame/dispatch [:hide-modal]))]]])))

(defn schedule-page-modal []
  [modal
   [schedule-page-form]])

(def-modal :add-page [add-page-modal])
(def-modal :delete-page [delete-page-modal])
(def-modal :schedule-page [schedule-page-modal])

;;;;

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
