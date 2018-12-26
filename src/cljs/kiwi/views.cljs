(ns kiwi.views
  (:require 
   [re-frame.core :as re-frame :refer [after dispatch dispatch-sync enrich path register-handler register-sub subscribe]]
   [re-com.core :as re-com]
   [clojure.string :as string]
   [kiwi.routes :as routes]
   [kiwi.page.core :as page]
   [kiwi.db :as page-db]
   [kiwi.features :as features])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))

(defn- dispatch-new-page! []
  (dispatch [:show-modal :add-page]))

(defn- layout-header []
  [:div.header
   [:div
    [:div.btn-group
     [:button.btn.btn-default {:on-click #(.back js/window.history)} [:i.fa.fa-angle-left] " Back"]
     [:a.btn.btn-default {:href (routes/page-route {:permalink "home"})} [:i.fa.fa-home] " Home"]]]
   [:nav.navigation
    [:div.btn-group
     [:a.btn.btn-default {:href (routes/settings-route)} [:i.fa.fa-cog] " Settings"]
     [:a.btn.btn-default {:href (routes/search-route)} [:i.fa.fa-search] " Search"]
     [:button.btn.btn-default
      {:on-click dispatch-new-page!}
      [:i.fa.fa-plus] " New page"]]]])

(defonce _modals (atom {}))

(defn tags-list
  ([opts tags]
   (when features/tags-enabled?
     [:ul
      (merge {:className "tags-list"} opts)
      (map (fn [tag] ^{:key tag}
             [:li
              [re-com/button
               :class "btn-tag"
               :label (str "#" tag)
               :on-click #(dispatch [:set-route (routes/search-route
                                                 {:query-params {:filter (str "tags:" tag)}})])]])
           tags)]))
  ([tags]
   (tags-list {} tags)))

(defn modals [id]
  (get @_modals id))

(defn def-modal [id view]
  (swap! _modals assoc id view))

(defn modal [content]
  [re-com/modal-panel
   :child content
   :backdrop-on-click #(dispatch [:hide-modal])])

(defn base-layout [content]
  (let [modal (subscribe [:modal])
        configured? (subscribe [:configured?])]
    (fn [content] 
      [:div
       (if (not @configured?)
         [:div
          [:div.header]
          [:section.content-wrapper
           [:div.content
            [kiwi.setup.views/setup-page]]]]

         [:div
          [layout-header]
          [:section.content-wrapper
           [:div.content
            content]]])
       (when @modal
         (modals @modal))])))

