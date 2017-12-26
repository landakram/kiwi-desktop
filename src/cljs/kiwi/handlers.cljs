;; * Imports / Utils
(ns kiwi.handlers
  (:require [kiwi.db :as page-db]
            [kiwi.history :refer [history]]
            [kiwi.page.core :as page]
            [kiwi.storage :as storage]
            [kiwi.utils :as utils]
            [kiwi.markdown-processors :as markdown-processors]
            [kiwi.editor.events]
            [kiwi.search.events]
            [kiwi.settings.events]
            [kiwi.page.events]
            [re-frame.core
             :as
             re-frame
             :refer
             [after enrich path reg-event-db reg-event-fx]]
            [secretary.core :as secretary]
            [cljs.core.async :as async])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn- set-hash!
  "Set the browser's location hash."
  [path]
  (set! (.-hash js/window.location) path))

;; * Initialization

(defonce initial-state {:current-route [:home-page]
                        :route-state {}})

(reg-event-db :initialize
  [(after kiwi.settings.events.set-page-db-wiki-dir!)]
  (fn [db [_ state]]
    ; Use initial-state as a default, but keep anything already in db
    (merge initial-state db (or state {}))))

(defn p-r [thing]
  (js/console.log thing)
  thing)

;; * Modals

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

;; * Navigation

(reg-event-db
 :navigate 
 (fn [db [_ route & [route-state]]]
   (-> db
       (assoc :current-route route)
       (assoc :route-state (or route-state {})))))

(reg-event-fx
 :set-route
 (fn [{:keys [db] as :cofx} [_ path]]
   (set-hash! path)
   {:db db}))

;; TODO: Move elsewhere
;; * Scheduling

(reg-event-db
 :assoc-google-access-token
 [(after #(storage/save! "google-access-token" (:google-access-token %)))]
 (fn [db [_ access-token]]
   (assoc db :google-access-token access-token)))


(reg-event-fx
 :add-metadata
 (fn [{ :keys [db]} [_ page metadata]]
   (let [processor (markdown-processors/metadata-processor metadata)
         page (get-in db [:route-state :page])
         old-content (get-in page [:contents])
         new-content (-> old-content
                         (processor.processSync)
                         (.toString))]
     (if (= old-content new-content)
       {:db db}
       {:db db
        :dispatch [:save-page new-content]}))))



(reg-event-fx
 :schedule-page
 (fn [{:keys [db]} [_ page js-start-date]]
   (let [processor (markdown-processors/scheduling-processor js-start-date)
         page (get-in db [:route-state :page])
         old-content (get-in page [:contents])
         new-content (-> old-content
                         (processor.processSync)
                         (.toString))]
     {:db db
      :dispatch [:save-page new-content]})))
