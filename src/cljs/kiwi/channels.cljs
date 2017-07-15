(ns kiwi.channels
  (:require-macros [cljs.core.async.macros :refer [go-loop go]])
  (:require [cljs.core.async :refer [chan put! timeout alts! <! >!]]))

(defn debounce [in ms]
  (let [out (chan)]
    (go-loop [last-val nil]
      (let [val (if (nil? last-val) (<! in) last-val)
            timer (timeout ms)
            [new-val ch] (alts! [in timer])]
        (condp = ch
          timer (do (>! out val) (recur nil))
          in (recur new-val))))
    out))


(def page-chan (chan))
(def page-changes (debounce page-chan 3000))

(defn put-page-chan [page] 
  (put! page-chan page))
