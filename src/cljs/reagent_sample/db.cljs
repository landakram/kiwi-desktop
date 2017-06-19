(ns reagent-sample.db
  (:require [cljsjs.dexie]
            [cljs.core.async :refer [chan <! put!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn create-db []
  (let [db (js/Dexie. "kiwi")]
    (-> db
        (.version 1)
        (.stores #js {"pages" "&permalink"
                      "images" "&path"}))
    (.open db)
    db))

(defonce db (create-db))

(set! (.-db js/window) db)

(defn save-in! [store data & [key]]
  (let [ch (chan)]
    (println "(save)" store)
    (-> (aget db store)
        (.put (clj->js data) key)
        (.then #(put! ch %)))
    ch))

(defn load-in [store key-name key]
  (let [ch (chan)]
    (println "(load)" key)
    (-> (aget db store)
        (.where key-name)
        (.equals key)
        (.first)
        (.then (fn [page] 
                 (let [result (if (nil? page)
                                :not-found
                                (js->clj page :keywordize-keys true))]
                   (put! ch result)))))
    ch))


;; db.pages.toCollection().reverse().sortBy("timestamp").then(p);
(defn load-all! []
  (let [ch (chan)]
    (println "(load) all")
    (-> (aget db "pages")
        (.toCollection)
        (.reverse)
        (.sortBy "timestamp")
        (.then #(put! ch (js->clj % :keywordize-keys true))))
    ch))

(defn load-permalinks []
  (let [ch (chan)]
    (println "(load) permalinks")
    (-> (aget db "pages")
        (.toCollection)
        (.keys)
        (.then (fn [permalinks]
                 (put! ch (set (js->clj permalinks))))))
    ch))


(defn save! [data]
  (save-in! "pages" data))

(defn load [key]
  (load-in "pages" "permalink" key))
