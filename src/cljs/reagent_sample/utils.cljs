(ns reagent-sample.utils
  (:require [cljs.core.async :refer [chan <! pipe]]))

(defn contains [string substring] 
  (not= -1 (.indexOf string substring)))

(defn contains-in [obj ks]
  (not (nil? (get-in obj ks))))

(defn pipe-transform [in xform]
  (let [out (chan 1 xform)]
    (pipe in out)
    out))
