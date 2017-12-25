(ns kiwi.utils
  (:require [cljs.core.async :refer [chan <! pipe]]))

(defn in? 
  "true if coll contains elm"
  [coll elm]  
  (some #(= elm %) coll))

(defn starts-with
  [string substring]
  (.startsWith string substring))

(defn contains
  "Returns true if substring is contained within string" 
  [string substring]
  (not= -1 (.indexOf string substring)))

(defn contains-in [obj ks]
  (not (nil? (get-in obj ks))))

(defn pipe-transform [in xform]
  (let [out (chan 1 xform)]
    (pipe in out)
    out))

(defn p-r [thing]
  (js/console.log thing)
  thing)

