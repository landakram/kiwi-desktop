(ns reagent-sample.sync
    (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                     [reagent-sample.macros :refer [<?]])
    (:require [reagent-sample.dropbox :as dropbox]
              [reagent-sample.utils :as utils]
              [reagent-sample.page :as page]
              [re-frame.core :refer [dispatch]]
              [re-frame.db :refer [app-db]]
              [clojure.string :as string]
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

(defn start []
  (dropbox/authenticate client false))

(start)


;(.readdir client "wiki"
          ;(fn [err entries dir-stat entry-stats]
            ;(println entries)))


;(p "what")

(defn path->permalink [path]
  (-> path 
      (string/split "/") 
      last 
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
     :timestamp timestamp
     :contents contents}))

(defonce poll-results (chan 1 (map ->poll-results)))
(defonce pull-results (chan 1 (map ->pull-results)))
(defonce page-results (chan 1 (map ->page-results)))

(defn throw-error [v]
  (if (-error? v)
    (throw v)
    v))

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
      (recur))))

(defonce page-loop
  (go-loop []
    (let [page (<! page-results)]
        (dispatch [:assoc-page page])
        (println page)
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
    (dropbox/write client path contents (filter-error ch))))
