(ns reagent-sample.dropbox)

(def AuthError (.-AuthError js/Dropbox))
(def ApiError (.-ApiError js/Dropbox))

(defn create-client [key]
  (let [Client (.-Client js/Dropbox)]
    (Client. #js {:key key})))

(defn authenticate [client interactive]
   (.authenticate client
                   #js {:interactive interactive}))
