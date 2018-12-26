(ns kiwi.tests
  (:require [cljs.test :refer-macros [run-tests run-all-tests]]
            [kiwi.handlers-test]
            [kiwi.settings.events-test]
            [kiwi.setup.events-test]
            [kiwi.editor.events-test]))


(defn run []
  (run-tests 'kiwi.handlers-test
             'kiwi.settings.events-test
             'kiwi.setup.events-test
             'kiwi.editor.events-test))
