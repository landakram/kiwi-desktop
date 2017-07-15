(ns reagent-sample.handlers
  (:require [re-frame.core :as re-frame :refer [after enrich path reg-event-db]]
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


(reg-event-db :initialize
  [(after set-page-db-wiki-dir!)]
  (fn [db [_ state]]
    ; Use initial-state as a default, but keep anything already in db
    (merge initial-state db (or state {}))))

(reg-event-db :page-edit 
  ; This vector is middleware. 
  ; The first one scopes app-db to the :page, so that the handler function below 
  ; it receives page instead of the full app-db.
  ; The second one puts the updated page on a chan, where subscribers can
  ; listen to it. This is used to save to localStorage / sync to dropbox.
  [(path :route-state :page) (after channels/put-page-chan)]
  (fn [page [_ contents]]
    (-> page
        (assoc-in [:contents] contents)
        (assoc-in [:timestamp] (js/Date.)))))

(reg-event-db
 :show-modal
 (path :route-state)
 (fn [route-state [_ modal-id]]
   (assoc route-state :modal modal-id)))

(reg-event-db
 :hide-modal
 (path :route-state)
 (fn [route-state [_]]
   (dissoc route-state :modal)))

(defn is-current-page [page [route-name path-arg]]
    (and (= :wiki-page-view route-name) 
         (= path-arg (:permalink page))))

(reg-event-db
 :assoc-editing?
 (fn [db [_ editing?]]
   (assoc-in db [:route-state :editing?] editing?)))

(reg-event-db
 :navigate 
 (fn [db [_ route & [route-state]]]
   (-> db
       (assoc :current-route route)
       (assoc :route-state (or route-state {})))))

(reg-event-db
 :assoc-search-filter
 (fn [db [_ filter]]
   (assoc-in db  [:route-state :filter] filter)))


(reg-event-db
 :assoc-wiki-root-dir
 [(after #(storage/save! "wiki-root-dir" (:wiki-root-dir %)))
  (after set-page-db-wiki-dir!)]
 (fn [db [_ wiki-root-dir]]
   (assoc db :wiki-root-dir wiki-root-dir)))


(reg-event-db
 :create-page
 (fn [db [_ page-title]]
   (let [permalink (page/get-permalink-from-title page-title)]
     (set-hash! (str "/page/" permalink))
     (print db)
     db)))
