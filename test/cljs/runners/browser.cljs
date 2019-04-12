(ns runners.browser
  (:require
   [cljs.test :refer-macros [run-tests]]
   [cljs-test-display.core]
   [kiwi.handlers-test]
   [kiwi.settings.events-test]
   [kiwi.setup.events-test]
   [kiwi.editor.events-test]))

(defn run []
  (run-tests
   (cljs-test-display.core/init! "app")
   'kiwi.handlers-test
   'kiwi.settings.events-test
   'kiwi.setup.events-test
   'kiwi.editor.events-test))

(run)
