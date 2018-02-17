(ns kiwi.page.markdown
  (:require [clojure.string :as string]
            [kiwi.db :as page-db]
            [re-frame.core :as re-frame :refer [after dispatch dispatch-sync enrich path register-handler register-sub subscribe]]
            [kiwi.markdown-processors :as markdown-processors]))

(defn path->filename [path]
  (-> path
      (string/split "/")
      last))

(defn create-dummy-node [html-contents]
  (let [el (.createElement js/document "div")]
    (set! (.-innerHTML el) html-contents)
    el))

(defn img-tag->path [root-dir img-tag]
  (let [src (string/lower-case (.-src img-tag))
        filename (path->filename src)
        path (str root-dir "/" page-db/img-rel-path filename)]
    path))

(defn is-local-img [src]
  (.startsWith src "file://"))

(defn rewrite-image-paths [root-dir html-contents]
  "Rewrite relative image paths like 'img/foo.jpg' to {root-dir}/public/img/foo.jpg"
  (let [el (create-dummy-node html-contents)
        img-nodes (.querySelectorAll el "img")]
        (doseq [img img-nodes]
          (let [src (.-src img)
                path (img-tag->path root-dir img)]
            (when (is-local-img src)
              (set! (.-src img) path))))
        (.-innerHTML el)))

(defn rewrite-external-links [html-contents]
  "Rewrite external links to open in a new window. Electron is set up to open these in the default browser."
  (let [el (create-dummy-node html-contents)
        links (.querySelectorAll el "a:not(.internal)")]
    (doseq [link links]
      (let [href (.-href link)]
        (set! (.-target link) "_blank")))
    (.-innerHTML el)))

(defn attach-checkbox-handlers [html-node]
  (let [nodes (.querySelectorAll html-node "input[type=checkbox]")]
    (doseq [node nodes]
      (set! (.-disabled node) false)
      (set! (.-onclick node)
            (fn [e]
              (this-as this
                (dispatch [:checkbox-toggle [(.-id (.closest this ".task-list-item"))]])
                (.preventDefault e)))))
    nodes))

(defn markdown->html [wiki-root-dir markdown permalinks]
  (let [^js/unified processor (markdown-processors/html-processor permalinks)
        html-contents (->> markdown
                            str
                            (.processSync processor)
                            (.toString)
                            (rewrite-image-paths wiki-root-dir)
                            (rewrite-external-links))]

    html-contents))

(defn highlight-code [html-node]
  (let [nodes (.querySelectorAll html-node "pre code")]
    (loop [i (.-length nodes)]
      (when-not (neg? i)
        (when-let [item (.item nodes i)]
          (.highlightBlock js/hljs item))
        (recur (dec i))))))
