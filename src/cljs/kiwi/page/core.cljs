(ns kiwi.page.core
  (:require [clojure.string :as string]
            [kiwi.utils :as utils]
            [kiwi.markdown-processors :as markdown-processors]))

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

(defn extract-yaml-node [ast]
  (first
   (filter #(= "yaml" (:type %))
           (:children ast))))

(defn extract-tags [ast]
  (let [yaml-node (extract-yaml-node ast)]
    (get-in yaml-node [:data :parsedValue :tags])))

(defn extract-scheduled [ast]
  (let [yaml-node (extract-yaml-node ast)
        date (get-in yaml-node [:data :parsedValue :scheduled])]
    (when date
      (js/Date. date))))

(defn extract-scheduled-id [ast]
  (let [yaml-node (extract-yaml-node ast)
        event-id (get-in yaml-node [:data :parsedValue :scheduled-id])]
    event-id))


(defn make-page [permalink contents modified-at]
  (let [processor (markdown-processors/ast-processor [])
        ast (markdown-processors/get-ast contents)]
    {:title (get-title-from-permalink permalink)
     :permalink permalink
     :contents contents
     :timestamp modified-at
     :scheduled (extract-scheduled ast)
     :scheduled-id (extract-scheduled-id ast)
     :tags (extract-tags ast)}))

(defn new-page [permalink]
  (make-page permalink "" (js/Date.)))


