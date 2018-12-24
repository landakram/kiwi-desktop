(ns kiwi.setup.views
  (:require
   [re-frame.core :as re-frame :refer [dispatch subscribe]]
   [re-com.core :as re-com]
   [re-frame.db :refer [app-db]]
   [kiwi.views :as views]
   [kiwi.setup.utils :as setup-utils]))

(def electron (js/require "electron"))
(def shell (.-shell electron))
(def remote (.-remote electron))
(def dialog (.-dialog remote))

(defn auto-detect-dropbox-alert []
  [re-com/alert-box
   :alert-type :info
   :heading "Dropbox auto-detected."
   :body [:div
          [:p "It looks like you have Dropbox already installed at " [:code setup-utils/default-dropbox-path] "."]
          [:p "Would you like to create your wiki automatically inside Dropbox?"]
          [:button.btn.btn-default
           {:on-click #(dispatch [:set-up-wiki
                                  (str setup-utils/default-dropbox-path "/Apps/KiwiApp")])}
           "Yes, please!"]]])

(defn create-wiki-button
  ([text]
   (create-wiki-button {} text))
  ([opts text]
   (let [on-directory-chosen
         (fn [directories]
           (when (seq directories)
             (dispatch [:set-up-wiki (str
                                      (first (js->clj directories))
                                      (:append opts))])))]

     [:button.btn.btn-default
      {:on-click (fn [_]
                   (.showOpenDialog dialog
                                    (clj->js {:properties ["openDirectory"]})
                                    on-directory-chosen))}
      text])))

(defn marketing-materials []
  (fn []
    [:div
     [:p "Kiwi is your personal wiki. You can use it to write, edit, and reflect on your thoughts. You might already use an app for taking notes, so here's why it's different:"]
     [:ul
      [:li "Link your notes together in whatever way makes sense to you instead of viewing them chronologically - you control the format."]
      [:li "Search your notes quickly and easily - find what you need."]
      [:li "Write your notes in markdown. No more WYSIWYG editors that don't do what you want."]
      [:li "Add photos, links, math notation, code snippets, and checkboxes. Flexibility is the name of the game."]
      [:li "Take your notes with you. Kiwi works offline and syncs automatically with Dropbox - you own your notes."]
      ]]))

(defn setup-intro []
  [:section
   [:h1.post-title "Welcome to Kiwi!"]
   [marketing-materials]
   [:button.btn.btn-default
    {:on-click #(dispatch [:navigate-setup :find-wiki])}
    "Set up"]])

(defn auto-wiki-setup []
  (fn []
    [:div
     [:h1 "Set up"]
     [:h2 "Wiki auto-detected."]
     [:p "Looks like you already have a wiki at " [:code setup-utils/default-wiki-path] "."]
     [:p "Would you like to configure Kiwi to use this wiki?"]
     [:div.btn-group
      [:button.btn.btn-default
       {:on-click #(dispatch [:set-up-wiki setup-utils/default-wiki-path])}
       "Yes, use that"]
      [:button.btn.btn-default
       {:on-click #(dispatch [:navigate-setup :create-wiki])}
       "No thanks"]]]))

(defn create-wiki []
  (fn []
    [:div
     [:h1 "Set up"]
     [:h2 "Create your wiki"]
     [:p "Let's get you set up with a wiki!"]
     [:p "A wiki is a collection of markdown files in a special directory structure that Kiwi understands."]
     [:h4 "Dropbox"]
     [:p "Set up your wiki inside Dropbox and take it everywhere you go with Kiwi for iOS."]
     (when (setup-utils/file-exists? setup-utils/default-dropbox-path)
       [auto-detect-dropbox-alert])
     [:p "Navigate to your Dropbox folder using the button below and Kiwi will do the rest."]
     [create-wiki-button
      {:append "/Apps/KiwiApp"}
      "Select Dropbox folder"]
     [:h4 "Elsewhere"]
     [:p "You can set up a wiki anywhere on your filesystem. Navigate to a folder where you want your wiki to live and Kiwi will set it up for you."]
     [create-wiki-button "Select wiki folder"]
     [:h4 "Already have a wiki?"]
     [:p "Welcome back. Tell Kiwi where it is using the button below."]
     [create-wiki-button "Link existing wiki"]]))

(defn find-wiki []
  (fn []
    [:section
     (if (setup-utils/valid-wiki? setup-utils/default-wiki-path)
       [auto-wiki-setup]
       ;; TODO: could put this in a handler by just having it decide what to do
       ;; and using an event like :navigate-setup-next or something
       (dispatch [:navigate-setup :create-wiki]))]))


(defn setup-page []
  (let [setup-route (subscribe [:setup-route])]
    (fn []
      [:article#page.settings
       (case @setup-route
         :find-wiki [find-wiki]
         :create-wiki [create-wiki]
         [setup-intro])])))
