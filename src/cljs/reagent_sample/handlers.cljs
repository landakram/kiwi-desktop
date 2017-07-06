(ns reagent-sample.handlers
  (:require [re-frame.core :as re-frame :refer [after enrich path register-handler]]
            [reagent-sample.channels :as channels]
            [reagent-sample.db :as page-db]
            [reagent-sample.storage :as storage]
            [reagent-sample.page :as page]
            [secretary.core :as secretary]
            [pushy.core :as pushy]
            [reagent-sample.history :refer [history]]))

(defonce initial-state {:current-route [:home-page]
                        :route-state {}})

(defn set-page-db-wiki-dir! [db]
  (page-db/set-wiki-dir! (:wiki-root-dir db)))

(defn- set-hash!
  "Set the browser's location hash."
  [path]
  (set! (.-hash js/window.location) path))


(register-handler :initialize
  [(after set-page-db-wiki-dir!)]
  (fn [db [_ state]]
    ; Use initial-state as a default, but keep anything already in db
    (merge initial-state db (or state {}))))

(register-handler :page-edit 
  ; This vector is middleware. 
  ; The first one scopes app-db to the :page, so that the handler function below 
  ; it receives page instead of the full app-db.
  ; The second one puts the updated page on a chan, where subscribers can
  ; listen to it. This is used to save to localStorage / sync to dropbox.
  [(path :route-state :page) (after channels/put-page-chan)]
  (fn [page [_ contents]]
    (-> page
      (assoc-in [:dirty?] true)
      (assoc-in [:contents] contents)
      (assoc-in [:timestamp] (js/Date.)))))

(defn save-page [when-to-save]
  (let [middleware (if (= :before when-to-save) enrich after)]
    (middleware (fn [db [_ page]] 
                  (page-db/save! page)
                  db))))

(register-handler
 :show-modal
 (path :route-state)
 (fn [route-state [_ modal-id]]
   (assoc route-state :modal modal-id)))

(register-handler
 :hide-modal
 (path :route-state)
 (fn [route-state [_]]
   (dissoc route-state :modal)))

(defn is-current-page [page [route-name path-arg]]
    (and (= :wiki-page-view route-name) 
         (= path-arg (:permalink page))))

(register-handler
 :assoc-dirty?
 [(save-page :after)]
 (fn [db [_ page dirty?]]
   (if (is-current-page page (:current-route db))
     (assoc-in db [:route-state :page :dirty?] dirty?)
     db)))



; Kind of a weird use of re-frame's handlers but:
;
; Here, I use the enrich middleware to *save whichever assoc'd page to localStorage*.
; I don't update db, because if the user has typed further, they will dirty the page
; and another sync will be run on its own.
(register-handler :assoc-page
  [(save-page :before)]
  (fn [db [_ page]]
    (let [current-page (get-in db [:route-state :page])]
      (if (is-current-page page (:current-route db))
        (when-not (:dirty? current-page)
          (assoc-in db [:route-state :page] page))
        db))))

(register-handler :pull-notes
  [ (after #(storage/save! "cursor" (:cursor %)))]
  (fn [db [_ pull-results]]
    (assoc db :cursor (:cursor pull-results))))

(register-handler :navigate 
  (fn [db [_ route & [route-state]]]
    (-> db
        (assoc :current-route route)
        (assoc :route-state (or route-state {})))))

(register-handler
 :linked-with-dropbox
 (fn [db [_ linked-with-dropbox?]]
   (if linked-with-dropbox?
     (sync/link)
     (sync/disconnect))
   (assoc db :linked-with-dropbox? linked-with-dropbox?)))
(register-handler
 :assoc-wiki-root-dir
 [(after #(storage/save! "wiki-root-dir" (:wiki-root-dir %)))
  (after set-page-db-wiki-dir!)]
 (fn [db [_ wiki-root-dir]]
   (assoc db :wiki-root-dir wiki-root-dir)))


(register-handler
 :create-page
 (fn [db [_ page-title]]
   (let [permalink (page/get-permalink-from-title page-title)]
     (set-hash! (str "/page/" permalink))
     (print db)
     db)))
