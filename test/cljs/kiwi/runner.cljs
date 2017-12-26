(ns kiwi.runner
  (:require [cljs.test :refer-macros [run-tests run-all-tests]]
            [kiwi.handlers-test]))


(defn run []
  (run-tests 'kiwi.handlers-test))
