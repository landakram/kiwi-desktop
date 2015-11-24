(ns reagent-sample.storage
  (:require [tailrecursion.cljson :refer [clj->cljson cljson->clj]]))

(defn save! [key data]
  (.setItem js/localStorage (name key) (clj->cljson data)))

(defn load [key]
  (cljson->clj (.getItem js/localStorage (name key))))
