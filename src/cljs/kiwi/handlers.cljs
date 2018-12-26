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
            [kiwi.setup.events]
            [kiwi.page.events]
            [re-frame.core
             :as
             re-frame
             :refer
             [after enrich path reg-event-db reg-event-fx reg-fx]]
            [secretary.core :as secretary]
            [cljs.core.async :as async])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

;; * Initialization

(defonce initial-state {:current-route [:home-page]
                        :route-state {}})

(reg-event-fx
 :initialize
 (fn [{:keys [db]} [_ state]]
 ; Use initial-state as a default, but keep anything already in db
   (let [new-db (merge initial-state db (or state {}))]
     {:db new-db
      :set-wiki-dir (new-db :wiki-root-dir)})))

(defn p-r [thing]
  (js/console.log thing)
  thing)

;; * Modals

(defn show-modal [route-state [_ modal-id]]
   (assoc route-state :modal modal-id))

(reg-event-db
 :show-modal
 (path :route-state)
 show-modal)

(reg-event-db
 :hide-modal
 (path :route-state)
 (fn [route-state [_]]
   (dissoc route-state :modal)))

;; * Navigation

(reg-fx
 :load-page
 (fn [permalink]
  (go
    (let [maybe-page (<! (page-db/load permalink))
          page (if (= maybe-page :not-found) 
                 (page/new-page permalink) 
                 maybe-page)
          permalinks (<! (page-db/load-permalinks))]
      (re-frame/dispatch
       [:navigate [:wiki-page-view permalink]
        {:path (str "/page/" permalink)
         :page page
         :permalinks permalinks
         :editing? false}])))))


(reg-event-fx
 :show-page
 (fn [{:keys [db]} [_ permalink]]
   {:db db
    :load-page permalink}))

(reg-fx
 :load-search-page
 (fn [filter]
   (go
    (let [pages (<! (page-db/load-all!))]
      (re-frame/dispatch [:navigate [:search-page]
                 {:path "/search"
                  :pages pages
                  :filter filter}])))))

(reg-event-fx
 :show-search-page
 (fn [{:keys [db]} [_ filter]]
   {:db db
    :load-search-page filter}))

(reg-event-fx
 :navigate 
 (fn [{:keys [db]} [_ route & [route-state]]]
   {:db
    (-> db
        (assoc :current-route route)
        (assoc :route-state (or route-state {})))
    :set-hash (route-state :path)}))

(defn- set-hash!
  "Set the browser's location hash."
  [path]
  (set! (.-hash js/window.location) path))

(reg-fx
 :set-hash
 (fn [path]
   (set-hash! path)))

(defn set-route [{:keys [db] as :cofx} [_ path]]
  {:db db
   :set-hash path})

(reg-event-fx
 :set-route
 set-route)

;; TODO: Move elsewhere
;; * Scheduling

(reg-fx
 :storage-save
 (fn [{:keys [key value]}]
   (storage/save! key value)))

(reg-event-fx
 :assoc-google-access-token
 (fn [{:keys [db]} [_ access-token]]
   {:db (assoc db :google-access-token access-token)
    :storage-save {:key "google-access-token" :value access-token}}))

;; TODO: move to page.events
(reg-event-fx
 :add-metadata
 (fn [{:keys [db]} [_ metadata]]
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
 (fn [{:keys [db]} [_ js-start-date]]
   (let [processor (markdown-processors/scheduling-processor js-start-date)
         page (get-in db [:route-state :page])
         old-content (get-in page [:contents])
         new-content (-> old-content
                         (processor.processSync)
                         (.toString))]
     {:db db
      :dispatch [:save-page new-content]})))
