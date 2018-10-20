(ns kiwi.settings.views
  (:require
   [re-frame.core :as re-frame :refer [after dispatch dispatch-sync enrich path register-handler register-sub subscribe]]

   [kiwi.google-calendar :as google-calendar]
   [kiwi.views :as views]
   [kiwi.keybindings :as keybindings]
   [kiwi.features :as features]
   [cljs.core.async :as async])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]
   [reagent.ratom :refer [reaction]])
  )

(def electron (js/require "electron"))
(def shell (.-shell electron))
(def remote (.-remote electron))
(def dialog (.-dialog remote))


(defn set-wiki-root-button []
  (let [on-directory-chosen
        (fn [directories]
          (when (seq directories)
            (dispatch [:assoc-wiki-root-dir (first (js->clj directories))])))]
    [:button.btn.btn-default
     {:on-click (fn [_]
                  (.showOpenDialog dialog
                                   (clj->js {:properties ["openDirectory"]})
                                   on-directory-chosen))}
     "Set wiki location"]))

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
         [:h2 "Wiki location"]
         (when (not (nil? @wiki-root-dir))
           [:p [ :code @wiki-root-dir]])
         [set-wiki-root-button]]
        (when features/schedule-enabled?
          [:section
           [:h2 "Link with Google Calendar"]
           (if @google-access-token
             [:div 
              [:p "Already linked with Google."]
              [link-with-google-button "Re-link with Google"]]
             [link-with-google-button])])

        #_[keybindings-help]]])))

