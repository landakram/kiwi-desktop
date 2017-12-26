(ns kiwi.handlers-test
  (:require [kiwi.handlers :as sut]
            [re-frame.core :as r]
            [day8.re-frame.test :as rf-test]
            [cljs.test :refer-macros [deftest is testing run-tests]]))

(defn test-fixtures [])

(deftest test-show-modal
  (rf-test/run-test-sync
   (test-fixtures)
   (r/dispatch [:initialize])

   (testing "updates state with a modal ID"
     (let [modal (r/subscribe [:modal])]
       (is (= @modal nil))
       (r/dispatch [:show-modal :modal-id])
       (is (= @modal :modal-id))))))
