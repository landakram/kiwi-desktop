(ns kiwi.handlers-test
  (:require [kiwi.handlers :as sut]
            [re-frame.core :as r]
            [day8.re-frame.test :as rf-test]
            [cljs.test :refer-macros [deftest is testing run-tests]]))


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
   (r/dispatch [:initialize])

   (testing "sets current-route"
     (let [current-route (r/subscribe [:current-route])]
       (r/dispatch [:navigate :some-page])
       (is (= @current-route :some-page))))))

(defn capture-into [atm]
  (fn [arg]
    (reset! atm arg)))

(deftest test-set-route
  (let [arg (atom nil)]
    (rf-test/run-test-sync
     (r/reg-fx :set-hash (capture-into arg))
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
     (r/reg-fx :storage-save (capture-into arg))
     (r/dispatch [:initialize])

     (testing "sets access token in state and saves to storage"
       (r/dispatch [:assoc-google-access-token "test-token"])
       (is (= @arg {:key "google-access-token" :value "test-token"}))
       (is (= @(r/subscribe [:google-access-token]) "test-token"))))))

(deftest test-add-metadata
  (rf-test/run-test-sync
   (r/reg-fx :save-page #())
   (r/reg-fx :schedule-page #())

   (r/dispatch [:initialize])
   (r/dispatch [:create-page "A Page"])

   (testing "sets metadata on page"
     (r/dispatch [:add-metadata {:metadata-key "metadata-value"}])
     (is (= (get-in
             @(r/subscribe [:current-page])
             [:metadata :metadata-key])
            "metadata-value")))))

(deftest test-schedule-page
  (rf-test/run-test-sync
   (r/reg-fx :save-page #())
   (r/reg-fx :schedule-page #())

   (r/dispatch [:initialize])
   (r/dispatch [:create-page "A Page"])

   (testing "sets scheduled date on page"
     (let [date (js/Date.)
           page (r/subscribe [:current-page])]
       (r/dispatch [:schedule-page date])
       (is (= (get @page :scheduled)
              date))))))
