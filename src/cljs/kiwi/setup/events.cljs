(ns kiwi.setup.events
  (:require
   [re-frame.core
    :as
    re-frame
    :refer
    [dispatch after enrich path reg-event-db reg-event-fx reg-fx]]
   [kiwi.setup.utils :as setup-utils]))

(def fs (js/require "fs-extra"))

(reg-event-fx
 :navigate-setup-next
 (fn [{:keys [db]} [_]]
   (let [route
         (if (setup-utils/valid-wiki? setup-utils/default-wiki-path)
           :find-wiki
           :create-wiki)]
     {:db
      (-> db
          (assoc :setup-state {:route route}))})))


(reg-event-fx
 :navigate-setup
 (fn [{:keys [db]} [_ route]]
   {:db
    (-> db
        (assoc :setup-state {:route route}))}))

(defn create-wiki-dirs [root-dir]
  (fs.mkdirpSync (str root-dir "/public/img"))
  (fs.mkdirpSync (str root-dir "/wiki")))

;; TODO: Replace with reading home file + other tutorial files
;;
;; Electron Builder's extraResources or extraFiles options
;; seems like the right way to get default pages into the app.
;;
;; The following link indicates that one should be able to use
;; `process.resourcesPath` to access the resources directory.
;;
;; The annoying thing is that process.resourcesPath references
;; /usr/lib/electron/resources when using `electron .`, so I
;; guess we'll have to deal with that.
;; 
;; https://discuss.atom.io/t/cant-find-file-when-using-fs-readfilesync-from-packaged-electron-app/24854/4

(def home-contents
  (str "## Welcome to Kiwi!\n\n"
       "This is your home page.\n\n"))

(defn create-default-home-page [root-dir]
  (let [home-path (str root-dir "/wiki/home.md")]
    (when (not (setup-utils/file-exists? home-path))
      (fs.writeFileSync home-path home-contents))))

(reg-event-fx 
 :finish-setup
 (fn [{:keys [db]} [_ root-dir]]
   (dispatch [:assoc-wiki-root-dir root-dir])
   (dispatch [:show-page "home"])
   {:db (dissoc db :setup-state)}))

(reg-fx
 :set-up-wiki
 (fn [root-dir]
   (create-wiki-dirs root-dir)
   (create-default-home-page root-dir)
   (dispatch [:finish-setup root-dir])))

(reg-event-fx
 :set-up-wiki
 (fn [{:keys [db]} [_ root-dir]]
   {:db db
    :set-up-wiki root-dir}))
