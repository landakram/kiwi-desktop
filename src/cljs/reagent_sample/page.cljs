(ns reagent-sample.page
  (:require [clojure.string :as string]))

(defn get-permalink-from-title [title]
  (string/replace (string/lower-case title) " " "-"))

(defn get-title-from-permalink [permalink]
  (string/replace (string/upper-case permalink) "-" " "))

(defn get-permalink [page]
  (get-permalink-from-title (:title page)))

(defn parse-wiki-links [html-content]
  (string/replace html-content #"\[\[(.*)\]\]"
    (fn [[_ page-title]]
      (str "<a class=\"internal\" href=\"/page/"
           (get-permalink-from-title page-title)
           "\">"
           page-title
           "</a>"))))

(defn markdown->html [page]
  (let [content (:contents page)]
    (-> content str js/marked parse-wiki-links)))
