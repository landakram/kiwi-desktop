(ns kiwi.settings.views
  (:require
   [re-frame.core :as re-frame :refer [after dispatch dispatch-sync enrich path register-handler register-sub subscribe]]

   [kiwi.google-calendar :as google-calendar]
   [kiwi.views :as views]
   [kiwi.keybindings :as keybindings]
   [kiwi.features :as features]
   [cljs.core.async :as async]
   [re-com.core :as re-com])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]
   [reagent.ratom :refer [reaction]])
  )



(defn link-with-google-button
  ([text]
   [:button.btn.btn-default
    {:on-click (fn [_]
                 (go
                   (let [token (async/<! (google-calendar/sign-in))]
                     (dispatch [:assoc-google-access-token token]))))}
    text])
  ([] 
   [link-with-google-button "Link with Google"]))


#_(dispatch [:assoc-wiki-root-dir nil])




(defn keybindings-help []
  [:section
   [:h2 "Keybindings"]
   [:p "Kiwi has a number of built-in " [:a {:href "https://www.vim.org/" :target "_blank"} "vim"] "-esque keybindings, for those who are keyboard-inclined."]
   [:table.table
    [:thead
     [:tr
      [:th "Key"]
      [:th "Description"]]]
    [:tbody
     (for [keybinding keybindings/keybindings]
       [:tr
        [:th [:code (:key keybinding)]]
        [:th (:description keybinding)]])]]])

(defn settings-page []
  (let [wiki-root-dir (subscribe [:wiki-root-dir])
        google-access-token (subscribe [:google-access-token])]
    (fn []
      [views/base-layout
       [:article#page.settings
        [:section 
         [:h1.post-title "Settings"]

         [:section
          [:h2 "Setup"]
          (if (not (nil? @wiki-root-dir))
            [:div
             [:p "Kiwi is configured to use your wiki located at " [:code @wiki-root-dir] "."]
             [:button.btn.btn-default
              {:on-click #(dispatch [:assoc-wiki-root-dir nil])}
              "Unlink wiki"]]
            [kiwi.setup.views/setup])]]
        
        (when features/schedule-enabled?
          [:section
           [:h2 "Link with Google Calendar"]
           (if @google-access-token
             [:div 
              [:p "Already linked with Google."]
              [link-with-google-button "Re-link with Google"]]
             [link-with-google-button])])

        #_[keybindings-help]]])))

