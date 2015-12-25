(ns reagent-sample.dropbox
  (:require [cljs.core.async :refer [chan <! put!]]))

(def AuthError (.-AuthError js/Dropbox))
(def ApiError (.-ApiError js/Dropbox))

(defn create-client [key]
  (let [Client (.-Client js/Dropbox)]
    (Client. #js {:key key})))

(defn authenticate [client interactive]
   (.authenticate client
                   #js {:interactive interactive}))

(defn ->chan
  ([ch]
    (fn [err value]
      (if err
        (put! ch err)
        (put! ch value))))
   ([ch xform]
    (fn [err value]
      (if err
        (put! ch err)
        (put! ch (xform value))))))

(defn pull-changes [client cursor ch]
  (.pullChanges client cursor (->chan ch)))

(defn poll [client cursor ch]
  (if cursor
    (.pollForChanges client cursor (->chan ch))
    (put! ch #js {:hasChanges true
                  :retryAfter 5})))

(defn read [client path ch]
  (println "(read)" path)
  (.readFile client path (->chan ch (fn [text] {:path path
                                                :contents text}))))

(defn write [client path text ch]
  (println "(write)" path)
  (.writeFile client path text (->chan ch)))
