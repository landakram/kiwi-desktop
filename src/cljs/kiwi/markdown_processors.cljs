(ns kiwi.markdown-processors)

(def markdown (js/require "remark-parse"))
(def remark-to-rehype (js/require "remark-rehype"))
(def fmt-html (js/require "rehype-format"))
(def html (js/require "rehype-stringify"))
(def unified (js/require "unified"))
(def task-list-plugin (js/require "remark-task-list"))
(def wiki-link-plugin (js/require "remark-wiki-link"))
(def yaml-plugin (js/require "remark-parse-yml"))
(def md-stringify (js/require "remark-stringify"))

(defn ast-processor [permalinks]
  (let [^js/unified ast-processor
        (-> (unified)
            (.use markdown (clj->js {:gfm true :footnotes true :yaml true}))
            (.use task-list-plugin (clj->js {}))
            (.use wiki-link-plugin (clj->js {"permalinks" permalinks}))
            (.use yaml-plugin))]
    ast-processor))

(defn html-processor [permalinks]
  (-> (ast-processor permalinks)
      (.use remark-to-rehype)
      (.use fmt-html)
      (.use html)))

(defn checkbox-toggling-processor [checkbox-id]
  (-> (unified)
      (.use markdown (clj->js {:gfm true :footnotes true :yaml true}))
      (.use md-stringify)
      (.use task-list-plugin (clj->js {:toggle [checkbox-id]}))
      (.use wiki-link-plugin (clj->js {}))
      (.use yaml-plugin)))

(defn get-ast [content]
  (let [processor (ast-processor [])]
    (-> content
        (processor.parse)
        (processor.runSync)
        (js->clj :keywordize-keys true))))
