(ns kiwi.storage)

(defn save! [key data]
  (println "(save)" key data)
  (.setItem js/localStorage (name key) (clj->js data)))

(defn load [key]
  (println "(load)" key)
  (js->clj (.getItem js/localStorage (name key))))
