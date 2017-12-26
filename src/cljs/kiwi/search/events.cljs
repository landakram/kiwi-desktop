(ns kiwi.search.events
  (:require [re-frame.core
             :as
             re-frame
             :refer
             [after enrich path reg-event-db reg-event-fx]]))

(reg-event-db
 :assoc-search-filter
 (fn [db [_ filter]]
   (assoc-in db  [:route-state :filter] filter)))
