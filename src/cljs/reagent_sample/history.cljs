(ns reagent-sample.history
  (:require [pushy.core  :as pushy]
            [secretary.core  :as secretary]))

(def history
  (pushy/pushy secretary/dispatch!
               (fn [x] (when (secretary/locate-route x) x))))
