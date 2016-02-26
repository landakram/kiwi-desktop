(ns reagent-sample.handlers
  (:require [re-frame.core :as re-frame :refer [after enrich path register-handler]]
            [reagent-sample.channels :as channels]
            [reagent-sample.db :as page-db]
            [reagent-sample.storage :as storage]
            [reagent-sample.page :as page]
            [reagent-sample.sync :as sync]))

(defonce initial-state {:current-route [:home-page {}]})

(register-handler :initialize
  (fn [db [_ state]]
    ; Use initial-state as a default, but keep anything already in db
    (merge initial-state db (or state {}))))

(register-handler :page-edit 
  ; This vector is middleware. 
  ; The first one scopes app-db to the :page, so that the handler function below 
  ; it receives page instead of the full app-db.
  ; The second one puts the updated page on a chan, where subscribers can
  ; listen to it. This is used to save to localStorage / sync to dropbox.
  [(path :current-route 1 :page) (after channels/put-page-chan)]
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
 :assoc-dirty?
 [(save-page :after)]
 (fn [db [_ page dirty?]]
   (if (page/is-current-page page (:current-route db))
     (assoc-in db [:current-route 1 :page :dirty?] dirty?)
     db)))

; Kind of a weird use of re-frame's handlers but:
;
; Here, I use the enrich middleware to *save whichever assoc'd page to localStorage*.
; I don't update db, because if the user has typed further, they will dirty the page
; and another sync will be run on its own.
(register-handler :assoc-page
  [(save-page :before)]
  (fn [db [_ page]] db))

(register-handler :pull-notes
  [ (after #(storage/save! "cursor" (:cursor %)))]
  (fn [db [_ pull-results]]
    (assoc db :cursor (:cursor pull-results))))

(register-handler :navigate 
  ;[(after notify-page-change)]
  (fn [db [_ route]]
    (assoc db :current-route route)))

(register-handler
 :linked-with-dropbox
 (fn [db [_ linked-with-dropbox?]]
   (if linked-with-dropbox?
     (sync/link)
     (sync/disconnect))
   (assoc db :linked-with-dropbox? linked-with-dropbox?)))
