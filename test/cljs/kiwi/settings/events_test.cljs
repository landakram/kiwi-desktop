(ns kiwi.settings.events-test
  (:require [kiwi.settings.events :as sut]
            [re-frame.core :as r]
            [devcards.core :refer-macros [deftest]]
            [day8.re-frame.test :as rf-test]
            [cljs.test :refer-macros [is testing run-tests]]))

(deftest test-assoc-wiki-root-dir
  (let [effects (sut/assoc-wiki-root-dir {} [:assoc-wiki-root-dir "wiki/root/dir"])]
    (is (= effects
           {:db {:wiki-root-dir "wiki/root/dir"}
            :set-wiki-dir "wiki/root/dir"
            :storage-save {:key "wiki-root-dir" :value "wiki/root/dir"}}))))
