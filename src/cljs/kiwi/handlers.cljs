(ns kiwi.handlers
  (:require [kiwi.channels :as channels]
            [kiwi.db :as page-db]
            [kiwi.history :refer [history]]
            [kiwi.page :as page]
            [kiwi.storage :as storage]
            [kiwi.utils :as utils]
            [pushy.core :as pushy]
            [re-frame.core
             :as
             re-frame
             :refer
             [after enrich path reg-event-db reg-event-fx]]
            [secretary.core :as secretary]))

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

(defn p-r [thing]
  (js/console.log thing)
  thing)

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
     #_(print db)
     db)))

(def markdown (js/require "remark-parse"))
(def md-stringify (js/require "remark-stringify"))
(def unified (js/require "unified"))
(def task-list-plugin (js/require "remark-task-list"))
(def ^js/unified md-processor (-> (unified)
                      (.use markdown (clj->js {:gfm true :footnotes true :yaml true}))
                      (.use md-stringify)))

(reg-event-fx
 :checkbox-toggle
 (fn [{:keys [db] :as cofx} [_ [checkbox-id]]]
   (let [processor (-> (md-processor)
                       (.use task-list-plugin (clj->js {"toggle" [checkbox-id]})))
         old-content (get-in db [:route-state :page :contents])
         new-content (-> old-content
                         (processor.processSync)
                         (.toString))]
     {:db db
      :dispatch [:page-edit new-content]})))
