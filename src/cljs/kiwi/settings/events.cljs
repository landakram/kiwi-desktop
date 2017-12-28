(ns kiwi.settings.events
  (:require
   [re-frame.core
    :as
    re-frame
    :refer
    [after enrich path reg-event-db reg-event-fx reg-fx]]
   [kiwi.storage :as storage]
   [kiwi.db :as page-db]))

(reg-fx
 :set-wiki-dir
 (fn [wiki-dir]
   (page-db/set-wiki-dir! wiki-dir)))

(defn assoc-wiki-root-dir [{:keys [db]} [_ wiki-root-dir]]
  {:db (assoc db :wiki-root-dir wiki-root-dir)
   :set-wiki-dir wiki-root-dir
   :storage-save {:key "wiki-root-dir" :value wiki-root-dir}})

(reg-event-fx
 :assoc-wiki-root-dir
 assoc-wiki-root-dir)
