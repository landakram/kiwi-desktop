(ns reagent-sample.db
  (:require [cljsjs.dexie]
            [cljs.core.async :refer [chan <! put!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn create-db []
  (let [db (js/Dexie. "kiwi")]
    (-> db
        (.version 1)
        (.stores #js {"pages" "&permalink"}))
    (.open db)
    db))

(defonce pages (create-db))

(defn save! [data]
  (let [ch (chan)]
    (println "(save)" (:permalink data))
    (-> (.-pages pages)
        (.put (clj->js data))
        (.then #(put! ch %)))
    ch))

(defn load [key]
  (let [ch (chan)]
    (println "(load)" key)
    (-> (.-pages pages)
        (.where "permalink")
        (.equals key)
        (.first)
        (.then #(put! ch (js->clj % :keywordize-keys true))))
    ch))
