(ns kiwi.setup.events-test
  (:require [kiwi.setup.events :as sut]
            [kiwi.test.utils :as t]
            [cljs.test :refer-macros [deftest is testing run-tests]]
            [day8.re-frame.test :as rf-test]
            [re-frame.core :as r]
            [kiwi.utils :as utils]))


(deftest test-navigate-setup-next
  (rf-test/run-test-sync
   (r/reg-cofx
    :default-wiki-path
    #(assoc % :default-wiki-path "/test"))

   (r/dispatch [:initialize])

   (let [route (r/subscribe [:setup-route])]
     (testing "with valid default wiki path"
       (r/reg-cofx
        :valid-wiki?
        #(assoc % :valid-wiki? (fn [p] true)))

       (testing "routes to :find-wiki"
         (r/dispatch [:navigate-setup-next])
         (is (= @route :find-wiki))))

     (testing "with invalid default wiki path"
       (r/reg-cofx
        :valid-wiki?
        #(assoc % :valid-wiki? (fn [p] false)))

       (testing "routes to :create-wiki"
         (r/dispatch [:navigate-setup-next])
         (is (= @route :create-wiki)))))))

(deftest test-navigate-setup
  (rf-test/run-test-sync
   (r/dispatch [:initialize])

   (let [route (r/subscribe [:setup-route])]
     (testing "sets route"
       (r/dispatch [:navigate-setup :test-route])
       (is (= @route :test-route))))))

(deftest test-set-up-wiki
  (let [arg (atom nil)]
    (rf-test/run-test-sync
     (r/reg-fx :set-up-wiki (t/capture-into arg))

     (r/dispatch [:initialize])

     (testing "causes a set-up-wiki effect"
       (r/dispatch [:set-up-wiki "/test"])
       (is (= @arg "/test"))))))

(deftest test-finish-setup
  (let [arg (atom nil)]
    (rf-test/run-test-sync
     (r/reg-fx :dispatch-n (t/capture-into arg))

     (r/dispatch [:initialize])

     (let [setup-route (r/subscribe [:setup-route])]
       (r/dispatch [:finish-setup "/test"])

       (testing "exits setup"
         (is (= @setup-route nil)))

       (testing "dispatches event to assoc wiki-root-dir"
         (is (utils/in? @arg [:assoc-wiki-root-dir "/test"])))

       (testing "dispatches event to navigate to wiki home"
         (is (utils/in? @arg [:show-page "home"])))))))
