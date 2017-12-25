(ns kiwi.subs
  (:require [kiwi.utils :as utils]
            [re-frame.core :as re-frame :refer [reg-sub]]
            [kiwi.markdown-processors :as markdown-processors]
            [clojure.string :as string])
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
 :google-access-token
 (fn [db _]
   (get-in db [:google-access-token])))

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
                        (.field this "tags")

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

(defn valid-filter? [filter]
  (and
   (> (.-length (string/trim filter)) 0)
   (not (.endsWith (string/trim filter) ":"))))

(defn search [index filter]
  (if (valid-filter? filter)
    (js->clj (.search index (str filter)))
    []))

(defn filter-pages [[ index pages filter-str] _]
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


(reg-sub
 :current-page-ast
 :<- [:current-page]
 :<- [:permalinks]
 (fn [[  current-page permalinks]]
   (markdown-processors/get-ast (:contents current-page))))

(reg-sub
 :edited-contents
 (fn [db _]
   (get-in db [:route-state :edit-state :contents])))
