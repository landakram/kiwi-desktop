(ns reagent-sample.sync
    (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                     [reagent-sample.macros :refer [<?]])
    (:require [reagent-sample.dropbox :as dropbox]
              [reagent-sample.utils :as utils]
              [reagent-sample.page :as page]
              [re-frame.core :refer [dispatch]]
              [re-frame.db :refer [app-db]]
              [clojure.string :as string]
              [reagent-sample.db :as db]
              [cljs.core.async :as async :refer [timeout chan put! <! pub sub]]))

(defonce dropbox-key "***REMOVED***")
(defonce client (dropbox/create-client dropbox-key))

(defprotocol IError
  (-error? [this]))

(extend-protocol IError
  js/Dropbox.AuthError
  (-error? [this] true)
  
  js/Dropbox.ApiError
  (-error? [this] true)
  
  js/Error
  (-error? [this] true))

(extend-type default
  IError
  (-error? [this] false))

(defn throw-error [v]
  (if (-error? v)
    (throw v)
    v))

(defn start []
  (dropbox/authenticate client false))

(start)

;; (.readdir client "public/img"
;;           (fn [err entries dir-stat entry-stats]
;;             (println entries)
;;             (let [ch (chan)]
;;               (go
;;                 (dropbox/read client (str "public/img/" (first entries)) ch)
;;                 (let [read-result (<? ch)]
;;                     (println read-result))))))


;(p "what")

(defn path->filename [path]
  (-> path
      (string/split "/")
      last))

(defn path->permalink [path]
  (-> (path->filename path) 
      (string/split ".") 
      first))

(defn ->pulled-change [result]
  (let [change {:path (.-path result)
                :deleted? false
                :timestamp nil}]
    (if (.-wasRemoved result)
      (assoc change :deleted? true)
      (assoc change :timestamp (-> result
                                   .-stat
                                   .-clientModifiedAt)))))

(defn ->pull-results [result]
  {:changes (map ->pulled-change (js->clj (.-changes result)))
   :cursor (.-cursorTag result)
   :pull-again? (.-shouldPullAgain result)})

(defn ->poll-results [result]
  {:has-changes? (.-hasChanges result)
   :retry-timeout (* 1000 (.-retryAfter result))})


(defn ->page-results [result]
  (let [{:keys [path contents timestamp]} result
        permalink (path->permalink path)]
    {:title (page/get-title-from-permalink permalink)
     :permalink permalink
     :timestamp timestamp
     :contents contents}))

(defn ->img-results [result]
  (let [{:keys [path contents mime_type]} result
        permalink (path->filename path)]
    {:path (str "img/" permalink)
     :contents contents
     :mime-type mime_type}))

(defonce poll-results (chan 1 (map ->poll-results)))
(defonce pull-results (chan 1 (map ->pull-results)))
(defonce page-results (chan 1 (map ->page-results)))
(defonce img-results (chan 1 (map ->img-results)))


(defn filter-error [outgoing-ch]
  (let [incoming-ch (chan)]
    (go
      (try
        (let [value (<? incoming-ch)]
          (put! outgoing-ch value))
        (catch js/Object e
          (println "(error)" e))))
    incoming-ch))

(defonce pull-loop
  (go-loop []
    (let [result (<! pull-results)
          changes (:changes result)
          img-changes (filter #(utils/contains (:path %) "img/") changes)
          page-changes (filter #(utils/contains (:path %) ".md") changes)]
      (println "(pull)" result)
      (dispatch [:pull-notes result])
      ; For each change detected from pulling, read in the wiki file
      (doseq [{:keys [path deleted? timestamp]} page-changes]
        (let [permalink (path->permalink path)
              ch (chan)]
            (dropbox/read client path ch)
            ; Add the timestamp into the final read data
            (let [read-result (<? ch)]
              (put! page-results (merge read-result {:timestamp timestamp})))))
      (doseq [{:keys [path deleted? timestamp]} img-changes]
        (let [ch (chan)]
          (dropbox/read client path ch :options {:blob true})
          (let [read-result (<? ch)]
            (put! img-results read-result))))
      (recur))))

(defonce page-loop
  (go-loop []
    (let [page (<! page-results)]
        (dispatch [:assoc-page page])
        (println page)
        (recur))))

(defonce img-loop
  (go-loop []
    (let [img (<! img-results)]
      (println "(image)" (:path img))
      (db/save-in! "images" img)
      (recur))))

; When started, triggers a poll every `:retry-timeout`
(defonce poll-loop
  (go-loop []
    (let [result (<! poll-results)
          cursor (:cursor @app-db)
          retry-timeout (:retry-timeout result)]
      (println "(poll)" result)
      (when (:has-changes? result)
        (dropbox/pull-changes client cursor (filter-error pull-results)))
      (<! (timeout (if (not= retry-timeout 0) retry-timeout 5000)))
      (dropbox/poll client cursor (filter-error poll-results))
      (recur))))

(defn start-polling [cursor]
  (dropbox/poll client cursor (filter-error poll-results)))

(defn write! [{:keys [title contents] :as page}]
  (let [ch (chan)
        permalink (page/get-permalink page)
        path (str "/wiki/" permalink ".md")]
    (println "(push)" path)
    (go
      (try
        (let [result (<? ch)]
          (println "(push) success"))
        (catch js/Object e
          (dispatch [:assoc-dirty? page true])
          (println "(push) (error)" e))))
    (dropbox/write client path contents ch)))
