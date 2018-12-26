(ns kiwi.setup.utils)

(def electron (js/require "electron"))
(def shell (.-shell electron))
(def remote (.-remote electron))
(def dialog (.-dialog remote))

(def fs (js/require "fs"))
(def os (js/require "os"))

(def default-dropbox-path
  (str (.-homedir (.userInfo os)) "/Dropbox"))
(def default-wiki-path
  (str default-dropbox-path "/Apps/KiwiApp"))

(defn file-exists? [path]
  (try
    (do
      (.accessSync fs path)
      true)
    (catch :default e
      false)))

(defn valid-wiki? [path]
  (let [sentinal (str path "/wiki/home.md")]
    (file-exists? sentinal)))
