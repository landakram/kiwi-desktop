(ns reagent-sample.page
  (:require [clojure.string :as string]))

(defn capitalize-words [s]
  (->> (string/split (str s) #"\b")
       (map string/capitalize)
       (string/join)))

(defn get-permalink-from-title [title]
  (string/replace (string/lower-case title) " " "_"))

(defn get-title-from-permalink [permalink]
  (-> permalink
      (string/replace "_" " ")
      (capitalize-words)))

(defn get-permalink [page]
  (:permalink page))

(defn parse-wiki-links [html-content]
  (string/replace html-content #"\[\[(.+?)\]\]"
    (fn [[_ page-title]]
      (str "<a class=\"internal\" href=\"/page/"
           (get-permalink-from-title page-title)
           "\">"
           page-title
           "</a>"))))

(defn markdown->html [page]
  (let [content (:contents page)]
    (-> content str js/marked parse-wiki-links)))

(defn new-page [permalink]
  {:title (get-title-from-permalink permalink)
   :permalink permalink
   :contents ""
   :timestamp (js/Date.)
   :dirty? true})

; Ugly, but checks whether the currently displayed wiki page is the page
(defn is-current-page [page [route-name route-args]]
    (and (= :wiki-page-view route-name) 
         (= (get-in route-args [:page :title]) (:title page))))

