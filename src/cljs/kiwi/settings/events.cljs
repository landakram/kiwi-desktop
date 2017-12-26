(ns kiwi.settings.events
  (:require
   [re-frame.core
    :as
    re-frame
    :refer
    [after enrich path reg-event-db reg-event-fx]]

   [kiwi.storage :as storage]
   [kiwi.db :as page-db]))

(defn set-page-db-wiki-dir! [db]
  (page-db/set-wiki-dir! (:wiki-root-dir db)))

(reg-event-db
 :assoc-wiki-root-dir
 [(after #(storage/save! "wiki-root-dir" (:wiki-root-dir %)))
  (after set-page-db-wiki-dir!)]
 (fn [db [_ wiki-root-dir]]
   (assoc db :wiki-root-dir wiki-root-dir)))
