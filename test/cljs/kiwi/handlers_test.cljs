(ns kiwi.handlers-test
  (:require [kiwi.handlers :as sut]
            [kiwi.core]
            [kiwi.test.utils :as t]
            [re-frame.core :as r] 
            [day8.re-frame.test :as rf-test]
            [cljs.test :refer-macros [deftest is testing run-tests]]
            [kiwi.routes :as routes]
            [kiwi.page.core :as page]))

(deftest test-show-modal
  (rf-test/run-test-sync
   (r/dispatch [:initialize])

   (testing "updates state with a modal ID"
     (let [modal (r/subscribe [:modal])]
       (is (= @modal nil))
       (r/dispatch [:show-modal :modal-id])
       (is (= @modal :modal-id))))))

(deftest test-navigate
  (rf-test/run-test-sync
   (r/reg-fx :set-hash #())

   (r/dispatch [:initialize])

   (testing "sets current-route"
     (let [current-route (r/subscribe [:current-route])]
       (r/dispatch [:navigate [:some-page] {:path ""}])
       (is (= @current-route [:some-page]))))))

(deftest test-set-route
  (let [arg (atom nil)]
    (rf-test/run-test-sync
     (r/reg-fx :set-hash (t/capture-into arg))
     (r/dispatch [:initialize])

     (testing "causes a set-hash effect"
       (r/dispatch [:set-route "/some/path"])
       (is (= @arg "/some/path"))))))

;; Another style of testing.
;; Advantage: event handler is a pure function, so we can test it like one
;; Disadvantage: subscribe is the public interfact to state changes, so we're
;;  kind of testing private functions.
(deftest test-set-route2
  (testing "causes a set-hash effect"
    (let [effects (sut/set-route {:db {}} [:set-route "/some/path"])]
      (is (= effects
             {:db {}
              :set-hash "/some/path"})))))

(deftest test-assoc-google-access-token
  (let [arg (atom nil)]
    (rf-test/run-test-sync
     (r/reg-fx :storage-save (t/capture-into arg))
     (r/dispatch [:initialize])

     (testing "sets access token in state and saves to storage"
       (r/dispatch [:assoc-google-access-token "test-token"])
       (is (= @arg {:key "google-access-token" :value "test-token"}))
       (is (= @(r/subscribe [:google-access-token]) "test-token"))))))

(deftest test-add-metadata
  (rf-test/run-test-async
   (r/reg-fx :save-page #())
   (r/reg-fx :schedule-page #())
   (r/reg-fx :set-hash #())

   (r/dispatch [:initialize])
   (r/dispatch [:create-page "A Page"])

   (rf-test/wait-for
    [:navigate]

    (r/dispatch [:add-metadata {:metadata-key "metadata-value"}])

    (rf-test/wait-for
     [:save-page]

     (testing "sets metadata on page"
       (is (= (get-in
               @(r/subscribe [:current-page])
               [:metadata :metadata-key])
              "metadata-value")))))))

(deftest test-schedule-page
  (rf-test/run-test-async
   (r/reg-fx :save-page #())
   (r/reg-fx :schedule-page #())
   (r/reg-fx :set-hash #())

   (r/dispatch [:initialize])
   (r/dispatch [:create-page "A Page"])

   ;; Note: test only works when this line is present:
   (rf-test/wait-for
    [:navigate]

    (let [date (js/Date.)
          page (r/subscribe [:current-page])]
      (r/dispatch [:schedule-page date])

      (rf-test/wait-for
       [:save-page]

       (testing "sets scheduled date on page"
         (is (= (get @page :scheduled)
                date))))))))

#_(run-tests)
