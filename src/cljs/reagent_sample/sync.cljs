(ns reagent-sample.sync
    (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                     [reagent-sample.macros :refer [<?]])
    (:require [reagent-sample.dropbox :as dropbox]
              [cljs.core.async :as async :refer [chan put! <! pub sub]]))

(defonce dropbox-key "***REMOVED***")
(defonce client (dropbox/create-client dropbox-key))

(defn start []
  (dropbox/authenticate client false))

(start)


;(.readdir client "wiki"
          ;(fn [err entries dir-stat entry-stats]
            ;(println entries)))


;(p "what")

(defonce channel (chan))

(defn error? [possible-err]
  (instance? js/Error possible-err))

(defn throw-error [v]
  (if (error? v)
    (throw v)
    v))

(go-loop []
  (try
    (let [{:keys [text]} (<? channel)]
      (println "text: " text))
    (catch js/Error e
      (println "error: " e)))
  (recur))
