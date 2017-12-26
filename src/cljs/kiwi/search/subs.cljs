(ns kiwi.search.subs
  (:require [kiwi.utils :as utils]
            [re-frame.core :as re-frame :refer [reg-sub]]
            [clojure.string :as string]))

(reg-sub 
 :all-pages
 (fn [db _]
   (get-in db [:route-state :pages])))

(reg-sub
 :search-filter
 (fn [db ]
   (get-in db [:route-state :filter])))


(def lunr (js/require "lunr"))
(set! (.-lunr js/window) lunr)

(defn- build-index [pages]
  (let [index (lunr (fn []
                      (this-as ^js/lunr.Index this
                        (.ref this "permalink")
                        (.field this "title")
                        (.field this "contents")
                        (.field this "tags")

                        (doseq [page pages]
                          (.add this (clj->js page))))))]
    (print "rebuilding index")
    index))

(reg-sub
 :search-index
 :<- [:all-pages]
 (fn [pages _]
   (build-index pages)))

(defn- valid-filter? [filter]
  (and
   (> (.-length (string/trim filter)) 0)
   (not (.endsWith (string/trim filter) ":"))))

(defn- search [index filter]
  (if (valid-filter? filter)
    (js->clj (.search index (str filter)))
    []))

(defn- filter-pages [[ index pages filter-str] _]
  (if (valid-filter? filter-str)
    (let [results (search index filter-str)
          permalinks (map #(get % "ref") results)
          filtered-pages (filter #(utils/in? permalinks (:permalink %)) pages)]
      filtered-pages)
    pages))

(reg-sub
 :filtered-pages
 :<- [:search-index]
 :<- [:all-pages]
 :<- [:search-filter]
 filter-pages)

