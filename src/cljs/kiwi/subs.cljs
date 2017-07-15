(ns kiwi.subs
  (:require [kiwi.utils :as utils]
            [re-frame.core :as re-frame :refer [reg-sub]])
  (:require-macros [reagent.ratom :refer [reaction]]))

(reg-sub 
 :all-pages
 (fn [db _]
   (get-in db [:route-state :pages])))

(reg-sub
 :search-filter
 (fn [db ]
   (get-in db [:route-state :filter])))

(reg-sub
 :permalinks
 (fn [db _]
   (get-in db [:route-state :permalinks])))

(reg-sub
 :editing?
 (fn [db _]
   (get-in db [:route-state :editing?])))

(reg-sub
 :current-page
 (fn [db _]
   (get-in db [:route-state :page])))

(reg-sub
 :wiki-root-dir
 (fn [db _]
   (get-in db [:wiki-root-dir])))

(reg-sub
 :modal
 (fn [db _]
   (get-in db [:route-state :modal])))

(reg-sub
 :current-route 
 (fn [db _] 
   (get-in db [:current-route])))

(def lunr (js/require "lunr"))
(set! (.-lunr js/window) lunr)

(defn build-index [pages]
  (let [index (lunr (fn []
                      (this-as ^js/lunr.Index this
                        (.ref this "permalink")
                        (.field this "title")
                        (.field this "contents")

                        (doseq [page pages]
                          (.add this (clj->js page))))))]
    (print "rebuilding index")
    #_(set! (.-index js/window) index)
    index))


(reg-sub
 :search-index
 :<- [:all-pages]
 (fn [pages _]
   (build-index pages)))

(defn filter-pages [[ index pages filter-str] _]
  (let [results (js->clj (.search index (str filter-str)))
        permalinks (map #(get % "ref") results)
        filtered-pages (filter #(utils/in? permalinks (:permalink %)) pages)]

    (if (> (.-length filter-str) 0)
      filtered-pages
      pages)
    ))

(reg-sub
 :filtered-pages
 :<- [:search-index]
 :<- [:all-pages]
 :<- [:search-filter]
 filter-pages)





#_(def markdown (js/require "remark-parse"))
#_(def md-stringify (js/require "remark-stringify"))
#_(def unified (js/require "unified"))
#_(def task-list-plugin (js/require "remark-task-list"))
#_(def md-processor (-> (unified)
                      (.use markdown (clj->js {:gfm true :footnotes true :yaml true}))
                      (.use md-stringify)))


#_(reg-sub
 :current-page-ast
 :<- [:current-page]
 (fn [current-page]
   (let [processor (-> (md-processor)
                       (.use task-list-plugin (clj->js {})))]
     (-> (:contents current-page)
         (processor.parse)
         (processor.runSync)
         (js->clj)))))
