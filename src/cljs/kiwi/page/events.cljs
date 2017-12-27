(ns kiwi.page.events
  (:require [kiwi.db :as page-db]
            [kiwi.page.core :as page]
            [kiwi.storage :as storage]
            [kiwi.utils :as utils]
            [kiwi.google-calendar :as google-calendar]
            [kiwi.markdown-processors :as markdown-processors]
            [re-frame.core
             :as
             re-frame
             :refer
             [after enrich path reg-event-db reg-event-fx reg-fx]]
            [cljs.core.async :as async])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

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
            (re-frame/dispatch [:add-metadata {:scheduled-id (get result "id")}])))))))


(defn save-page! [page]
  (page-db/save! page))

(reg-fx
 :save-page
 save-page!)

(reg-fx
 :schedule-page
 schedule-page!)

(reg-event-fx
 :save-page
 [(path :route-state :page)]
 (fn [{:keys [db]} [_ edited-contents]]
   (let [permalink (db :permalink)
         page (page/make-page permalink edited-contents (js/Date.))]
     {:db page
      :save-page page
      :schedule-page page})))

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
