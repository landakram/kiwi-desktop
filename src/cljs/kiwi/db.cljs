(ns kiwi.db
  (:require [cljs.core.async :as async :refer [<! >! chan put! take!]]
            [clojure.string :as string]
            [kiwi.page.core :as page] 
            [kiwi.utils :as utils])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(def fs (js/require "fs"))

(defonce db nil)
(defonce wiki-dir (atom ""))

(defonce wiki-rel-path "wiki/")
(defonce img-rel-path "public/img/")

(set! (.-db js/window) db)

(defn set-wiki-dir! [new-wiki-dir]
  (reset! wiki-dir new-wiki-dir))

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

(defn readdir [path]
  (let [ch (chan)]
    (.readdir fs
              path
              (fn [err files]
                (let [files (js->clj files)]
                  (async/onto-chan ch files))))
    ch))

(defn markdown-file? [path]
  (utils/contains path ".md"))

(defn path->permalink [path]
  (first (string/split path ".")))

(defn load-permalinks []
  (let [in (readdir (str @wiki-dir "/" wiki-rel-path))
        out (async/chan 1 (comp
                           (filter markdown-file?)
                           (map path->permalink)))]
    (async/pipe in out)
    (async/into [] out)))

(defn load [key]
  (let [ch (chan)
        path (str @wiki-dir "/" wiki-rel-path key ".md")]
    (.readFile fs
               path
               (fn [err data]
                 (if data
                   (let [stat (.statSync fs path)
                         contents (str data)
                         modified-at (.-mtime stat)
                         page (page/make-page key contents modified-at)]
                     (put! ch page))
                   (put! ch :not-found))
                 (async/close! ch)))
    ch))

(defn collect [ch]
  (async/reduce conj [] ch))

(defn load-pages []
  (let [out (chan)]
    (go
      (let [permalinks (<! (load-permalinks))
            page-chans (map load permalinks)]
        (go-loop [chans page-chans]
          (when (not (empty? chans))
            (let [page (<! (first chans))]
              (>! out page)
              (recur (rest chans))))
          (async/close! out))))
    out))

(defn load-all! []
  (let [ch (async/into [] (load-pages))
        out (chan)]
    ;; Sort pages by most recent to least recent
    (go-loop [pages (<! ch)]
      (when (not (nil? pages))
        (put! out (reverse (sort-by :timestamp pages)))
        (recur (<! ch))))
    out))

(defn save! [{:keys [permalink contents] :as page}]
  (let [ch (chan)
        path (str @wiki-dir "/" wiki-rel-path permalink ".md")]
    (.writeFile fs
                path
                contents
                (fn [err]
                  (put! ch :written)))
    ch))

(defn delete! [{:keys [permalink] :as page}]
  (let [ch (chan)
        path (str @wiki-dir "/" wiki-rel-path permalink ".md")]
    (.unlink fs
             path
             (fn [err]
               (print err)
               (put! ch :deleted)))
    ch))
