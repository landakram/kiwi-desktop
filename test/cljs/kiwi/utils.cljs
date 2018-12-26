(ns kiwi.test.utils)

(defn capture-into [atm]
  (fn [arg]
    (reset! atm arg)))
