(ns kiwi.editor.events-test
  (:require [kiwi.editor.events :as sut]
            [kiwi.core]
            [re-frame.core :as r] 
            [day8.re-frame.test :as rf-test]
            [devcards.core :refer-macros [deftest]]
            [cljs.test :refer-macros [is testing run-tests]]))

(deftest test-editing
  (let [editing? (r/subscribe [:editing?])
        edited-contents (r/subscribe [:edited-contents])
        page (r/subscribe [:current-page])]
    (rf-test/run-test-async
     (r/reg-fx :save-page #())
     (r/reg-fx :schedule-page #())
     (r/reg-fx :set-hash #())

     (r/dispatch [:initialize])
     (r/dispatch [:create-page "A Page"])

     (rf-test/wait-for
      [:navigate]

      (testing ":assoc-editing?"
        (testing "sets editing? to true"
          (r/dispatch-sync [:assoc-editing? true])
          (is (= @editing? true))))

      (testing "edit-page"
        (testing "sets edited-contents"
          (r/dispatch-sync [:edit-page "Test contents"])
          (is (= @edited-contents "Test contents"))))

      (testing ":assoc-editing?"
        (testing "sets editing? to false"
          (r/dispatch-sync [:assoc-editing? false])
          (is (= @editing? false))

          (rf-test/wait-for
           [:save-page]

           (testing ":assoc-editing?"
             (testing "sets page contents"
               (is (= (get @page :contents) "Test contents")))))))))))
