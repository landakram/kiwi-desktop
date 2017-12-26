(ns kiwi.search.views
  (:require [cljs-time.coerce :as coerce]
            [cljs-time.format :as f]
            [re-com.core :as re-com]
            [reagent.core :as reagent]
            [clojure.string :as string]
            [re-frame.core :as re-frame :refer [after dispatch dispatch-sync enrich path register-handler register-sub subscribe]]
            [kiwi.features :as features]
            [kiwi.routes :as routes]
            [kiwi.views :as views]))

;; TODO: duplicated in core.cljs
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

(defn page-list-item [page]
  (let [title (:title page)
        tags (:tags page)
        contents (:contents page)
        preview (->> (string/split contents #" ")
                     (take 20)
                     (string/join " "))
        permalink (:permalink page)
        date-format (f/formatter "MMMM d, yyyy")
        date (f/unparse date-format (coerce/from-date (:timestamp page)))
        path (str "#/page/" permalink)]
    ^{:key permalink}
    [:li
     [:div.row 
      [:div.col-xs
       [:h3.float-xs-left [:a.page-link.internal {:href path} title]]
       [:span.page-date.float-xs-right date]]]
     [:div.row
      [:div.col-xs
       [:p.page-preview (str preview "...")]
       [tags-list {:className "tags-preview"} tags]]]]))

(defn search-page []
  (let [filtered-pages (subscribe [:filtered-pages])
        search-text (subscribe [:search-filter])]
    (reagent/create-class
     {:component-did-mount 
      (fn []
        (print "did-mount"))
      :reagent-render
      (fn []
        [views/base-layout
          [:article#page
           [:h1.post-title "Search"]
           [re-com/input-text
            :attr {:auto-focus true}
            :change-on-blur? false
            :model search-text
            :width "100%"
            :style {:font-size "16px"}
            :placeholder "Search..."
            :on-change #(dispatch [:assoc-search-filter %])]
           [:ul.page-list
            (map page-list-item @filtered-pages)]]])})))

