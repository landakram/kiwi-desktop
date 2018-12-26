(ns kiwi.setup.events
  (:require
   [re-frame.core
    :as
    re-frame
    :refer
    [dispatch after enrich path reg-event-db reg-event-fx reg-fx reg-cofx inject-cofx]]
   [kiwi.setup.utils :as setup-utils]))

(def fs (js/require "fs-extra"))

(reg-cofx
 :default-wiki-path
 (fn [coeffects _]
   (assoc coeffects :default-wiki-path setup-utils/default-wiki-path)))

;; Seems like a misuse of cofx, but it makes
;; testing easier because we can stub it, so whatever.
;;
;; Then again, the valid-wiki? function internally is
;; not actually pure (uses the fs module), so it kind of
;; makes sense that it would be considered a coeffect.
(reg-cofx
 :valid-wiki?
 (fn [coeffects _]
   (assoc coeffects :valid-wiki? setup-utils/valid-wiki?)))

(reg-event-fx
 :navigate-setup-next
 [(inject-cofx :default-wiki-path)
  (inject-cofx :valid-wiki?)]
 (fn [{:keys [db default-wiki-path valid-wiki?] :as cofx} [_]]
   (let [route
         (if (valid-wiki? default-wiki-path)
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
   {:db (dissoc db :setup-state)
    :dispatch-n [[:assoc-wiki-root-dir root-dir]
                 [:show-page "home"]]}))

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
