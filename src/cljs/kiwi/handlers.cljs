;; * Imports / Utils
(ns kiwi.handlers
  (:require [kiwi.db :as page-db]
            [kiwi.history :refer [history]]
            [kiwi.page :as page]
            [kiwi.storage :as storage]
            [kiwi.utils :as utils]
            [kiwi.google-calendar :as google-calendar]
            [kiwi.markdown-processors :as markdown-processors]
            [kiwi.editor.events]
            [kiwi.search.events]
            [kiwi.settings.events]
            [pushy.core :as pushy]
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

;; * Page Editing

(def sugar (js/require "sugar-date"))

(defn schedule-page! [page]
  (let [token @(re-frame/subscribe [:google-access-token])
        js-start-date (get page :scheduled)
        event-id (get page :scheduled-id)]
    (when (not (nil? js-start-date))
      (go
        ;; TODO: handle error
        (let [result (if (nil? event-id)
                       (async/<! (google-calendar/create-event
                                  {:summary (:title page)
                                   :description (:contents page)
                                   :start {:dateTime js-start-date}
                                   :end {:dateTime (-> (js/Date. js-start-date)
                                                       (sugar.Date.)
                                                       (.advance #js {:hours 1})
                                                       (.-raw))}}
                                  token))
                       (async/<! (google-calendar/update-event
                                  {:event-id event-id
                                   :summary (:title page)
                                   :description (:contents page)
                                   :start {:dateTime js-start-date}
                                   :end {:dateTime (-> (js/Date. js-start-date)
                                                       (sugar.Date.)
                                                       (.advance #js {:hours 1})
                                                       (.-raw))}}
                                  token)))]
          (when (get result "accessTokens")
            (re-frame/dispatch-sync [:assoc-google-access-token (get result "accessTokens")]))
          (when (get result "id")
            (re-frame/dispatch [:add-metadata page {:scheduled-id (get result "id")}])))))))


(defn save-page! [page]
  (page-db/save! page))

(reg-event-db
 :save-page
 [(path :route-state :page)
  (after save-page!)
  (after schedule-page!)]
 (fn [{:keys [permalink] :as page} [_ edited-contents]]
   (page/make-page permalink edited-contents (js/Date.))))

(reg-event-fx
 :create-page
 (fn [{:keys [db]} [_ page-title]]
   (let [permalink (page/get-permalink-from-title page-title)]
     {:db db
      :dispatch [:set-route (str "/page/" permalink)]})))

(reg-event-fx
 :checkbox-toggle
 (fn [{:keys [db] :as cofx} [_ [checkbox-id]]]
   (let [processor (markdown-processors/checkbox-toggling-processor checkbox-id)
         page (get-in db [:route-state :page])
         old-content (get-in page [:contents])
         new-content (-> old-content
                         (processor.processSync)
                         (.toString))]
     {:db db
      :dispatch [:save-page new-content]})))

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

;; * Settings

