(ns kiwi.google-calendar
  (:require [cljs.core.async :as async]
            [re-frame.core :as re-frame])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def ^js/GoogleApis google (js/require "googleapis"))
(def electron-google-auth (js/require "electron-google-auth"))
(def sugar (js/require "sugar-date"))
(def client-id "1066590255852-o6k79jfc8vjg6i37k26p3ogb5fbdvkrs.apps.googleusercontent.com")
(def redirect-uri "com.markhudnall.kiwi:/google/oauth")

(defn sign-in []
  (let [out (async/chan)]
    (->
     (.googleSignIn electron-google-auth client-id redirect-uri)
     (.then (fn [token]
              (->> token
                   (js->clj)
                   (async/put! out))
              (async/close! out))))
    out))

(defn refresh-access-token [tokens]
  (let [out (async/chan)]
    (-> 
     (.refreshAccessToken electron-google-auth (get tokens "refresh_token") client-id redirect-uri)
     (.then (fn [token]
              (-> token
                  (js->clj)
                  (#(async/put! out
                                 (assoc tokens "access_token"
                                        (get % "access_token")))))
              (async/close! out))))
    out))

(def calendar (google.calendar "v3"))

(defn callback->chan [chan additional-params]
  (fn [err data]
    (when err
      (async/put! chan err))
    (when data
      (async/put! chan (merge (js->clj data) additional-params)))
    (async/close! chan)))

(defn create-event [params auth-tokens]
  (let [out (async/chan)]
    (go 
      (let [new-tokens (async/<! (refresh-access-token auth-tokens))
            client (.getAuthorizedOAuth2Client electron-google-auth (clj->js new-tokens))]
        (calendar.events.insert
         (clj->js {:auth client
                   :calendarId "primary"
                   :resource params})
         (callback->chan out {"accessTokens" new-tokens}))))
    out))

(defn update-event [params auth-tokens]
  (let [out (async/chan)]
    (go 
      (let [new-tokens (async/<! (refresh-access-token auth-tokens))
            client (.getAuthorizedOAuth2Client electron-google-auth (clj->js new-tokens))]
        (calendar.events.update
         (clj->js {:auth client
                   :calendarId "primary"
                   :eventId (get params :event-id)
                   :resource params})
         (callback->chan out {"accessTokens" new-tokens}))))
    out))
