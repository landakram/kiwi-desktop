(ns kiwi.page
  (:require [clojure.string :as string]
            [kiwi.utils :as utils]))

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

(defn title [page]
  (-> (get-permalink page)
      (get-title-from-permalink)))

(defn- parse-alias-link [page-title]
  (let [[name alias] (string/split page-title #":")]
    [alias name]))

(defn- alias? [page-title]
  (utils/contains page-title ":"))

(defn parse-page-title [page-title]
  (if (alias? page-title)
    (parse-alias-link page-title)
    [page-title page-title]))

(defn- construct-classes [name permalinks]
  (if (utils/in? permalinks name)
    ["internal"]
    ["internal" "new"]))

(defn parse-wiki-links [html-content permalinks]
  (string/replace
   html-content
   #"\[\[(.+?)\]\]"
   (fn [[_ page-title]]
     (let [[name display-name] (parse-page-title page-title)
           permalink (get-permalink-from-title name)
           classes (string/join " " (construct-classes permalink permalinks))]
       (str "<a class=\" " classes "\" href=\""
            "#" "/page/" permalink
            "\">"
            display-name
            "</a>")))))


(defn new-page [permalink]
  {:title (get-title-from-permalink permalink)
   :permalink permalink
   :contents ""
   :timestamp (js/Date.)})

