(ns kiwi.markdown-processors
  (:require [kiwi.utils :as utils]))

(def markdown (js/require "remark-parse"))
(def remark-to-rehype (js/require "remark-rehype"))
(def fmt-html (js/require "rehype-format"))
(def html (js/require "rehype-stringify"))
(def unified (js/require "unified"))
(def task-list-plugin (js/require "remark-task-list"))
(def wiki-link-plugin (js/require "remark-wiki-link"))
(def yaml-plugin (js/require "remark-parse-yaml"))
(def md-stringify (js/require "remark-stringify"))

(defn ast-processor [permalinks]
  (let [^js/unified ast-processor
        (-> (unified)
            (.use markdown #js {:gfm true :footnotes true :yaml true})
            (.use task-list-plugin #js {})
            (.use wiki-link-plugin #js {"permalinks" permalinks})
            (.use yaml-plugin))]
    ast-processor))

(defn html-processor [permalinks]
  (-> (ast-processor permalinks)
      (.use remark-to-rehype)
      (.use fmt-html)
      (.use html)))

(defn checkbox-toggling-processor [checkbox-id]
  (-> ^js/unified (unified)
      (.use markdown #js {:gfm true :footnotes true :yaml true})
      (.use md-stringify #js {:listItemIndent "1"})
      (.use task-list-plugin #js {:toggle #js [checkbox-id]})
      (.use wiki-link-plugin #js {})
      (.use yaml-plugin)))

(def ast-map (js/require "unist-util-map"))

(defn has-yaml-node? [ast]
  (let [yaml-node (first
                   (filter #(= "yaml" (:type %))
                           (:children ast)))]
    (not (nil? yaml-node))))

(defn metadata-plugin [opts]
  "Inserts metadata into YAML frontmatter. A new YAML node is created if one does not already exist."
  (let [{:keys [metadata]} opts]
    (fn [js-ast]
      (let [ast (js->clj js-ast :keywordize-keys true)
            children (:children ast)]
        (if (has-yaml-node? ast)
          (do
            (ast-map js-ast (fn [js-node]
                           (let [node (js->clj js-node :keywordize-keys true)]
                             (clj->js
                              (if (= "yaml" (:type node))
                                (do
                                  (update-in node [:data :parsedValue] merge metadata))
                                node))))))
          (clj->js
           (utils/p-r
            (assoc ast :children 
                   (into [{:type "yaml"
                           :data {:parsedValue metadata}}]
                         children)))))))))


(defn scheduling-plugin [opts]
  (let [{:keys [js-start-date]} opts]
    (metadata-plugin {:metadata {:scheduled js-start-date}})))

(defn metadata-processor [metadata]
  (-> ^js/unified (unified)
      (.use markdown (clj->js {:gfm true :footnotes true :yaml true}))
      (.use md-stringify (clj->js {:listItemIndent "1"}))
      (.use wiki-link-plugin (clj->js {}))
      (.use yaml-plugin)
      (.use metadata-plugin {:metadata metadata})))

(defn scheduling-processor [js-start-date]
  (-> ^js/unified (unified)
      (.use markdown (clj->js {:gfm true :footnotes true :yaml true}))
      (.use md-stringify (clj->js {:listItemIndent "1"}))
      (.use wiki-link-plugin (clj->js {}))
      (.use yaml-plugin)
      (.use scheduling-plugin {:js-start-date js-start-date})))


(defn get-ast [content]
  (let [^js/unified processor (ast-processor [])]
    (-> content
        (processor.parse)
        (processor.runSync)
        (js->clj :keywordize-keys true))))
