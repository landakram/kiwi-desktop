(ns kiwi.setup.views
  (:require
   [re-frame.core :as re-frame :refer [dispatch subscribe]]
   [re-com.core :as re-com]))

(def electron (js/require "electron"))
(def shell (.-shell electron))
(def remote (.-remote electron))
(def dialog (.-dialog remote))

(def fs (js/require "fs"))
(def os (js/require "os"))
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
   :heading "Wiki auto-detected."
   :body [:div
          [:p "Looks like you already have a wiki at " [:code default-wiki-path] "."]
          [:p "Would you like to configure Kiwi to use this wiki?"]
          [:button.btn.btn-default
           {:on-click #(dispatch [:assoc-wiki-root-dir default-wiki-path])}
           "Yes, please!"]]])

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


(defn setup []
  (fn []
    [:section
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

(defn setup-page []
  (fn []
    [:article#page.settings
      [:section
       [:h1.post-title "Welcome to Kiwi!"]
       [marketing-materials]

       [:section
        [:h2 "Setup"]
        [setup]]]]))
