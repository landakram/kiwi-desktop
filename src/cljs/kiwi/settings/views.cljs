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

(def electron (js/require "electron"))
(def shell (.-shell electron))
(def remote (.-remote electron))
(def dialog (.-dialog remote))

(def fs (js/require "fs"))
(def os (js/require "os"))


(defn set-wiki-root-button [text]
  (let [on-directory-chosen
        (fn [directories]
          (when (seq directories)
            (dispatch [:assoc-wiki-root-dir (first (js->clj directories))])))]
    [:button.btn.btn-default
      {:on-click (fn [_]
                   (.showOpenDialog dialog
                                    (clj->js {:properties ["openDirectory"]})
                                    on-directory-chosen))}
      text]))


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

(def default-wiki-path
  (str (.-homedir (.userInfo os)) "/Dropbox/Apps/KiwiApp"))

(defn file-exists? [path]
  (try
    (do
      (.accessSync fs path)
      true)
    (catch :default e
      false)))

(defn auto-setup-alert []
  [re-com/alert-box
   :alert-type :info
   :heading "Already have a wiki?"
   :body [:div
          [:p "It looks like you already have a wiki at " [:code default-wiki-path] "."]
          [:p "Would you like to use this wiki?"]
          [:button.btn.btn-default
           {:on-click #(dispatch [:assoc-wiki-root-dir default-wiki-path])}
           "Yes, please!"]]])

(defn setup []
  (fn []
    [:section
     [:h2 "Setup"]
     (when (file-exists? default-wiki-path)
       [auto-setup-alert])

     [:div
      [:p
       "Configure Kiwi by telling it where to find your wiki. If you use Kiwi for iOS, your wiki is located at "
       [:code "/Users/you/Dropbox/Apps/KiwiApp"]
       "."]
      [:div.btn-group
       [set-wiki-root-button "Find existing wiki"]
       [:button.btn.btn-default "Create new wiki"]]]]))

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

         (if (not (nil? @wiki-root-dir))
           [:section
            [:p "Your wiki is located at "[ :code @wiki-root-dir]]
            [set-wiki-root-button "Choose different location"]]
           [setup])]
        
        (when features/schedule-enabled?
          [:section
           [:h2 "Link with Google Calendar"]
           (if @google-access-token
             [:div 
              [:p "Already linked with Google."]
              [link-with-google-button "Re-link with Google"]]
             [link-with-google-button])])

        #_[keybindings-help]]])))

