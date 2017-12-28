(ns kiwi.routes
  (:require [secretary.core :as secretary :include-macros true]
            [kiwi.page.core :as page]
            [re-frame.core :as re-frame :refer [after dispatch dispatch-sync enrich path register-handler register-sub subscribe]]
            [kiwi.db :as page-db])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]
   [reagent.ratom :refer [reaction]]))

(def dispatch! secretary/dispatch!)

(secretary/set-config! :prefix "#")

(secretary/defroute index-route "/" []
  (dispatch [:navigate [:home-page] {:path "/"}]))

(secretary/defroute settings-route "/settings" []
  (dispatch [:navigate [:settings-page] {:path "/settings"}]))

(secretary/defroute search-route "/search" [_ query-params]
  (let [filter (get query-params :filter "")]
    (dispatch [:show-search-page filter])))

(secretary/defroute page-route "/page/:permalink" [permalink]
  (dispatch [:show-page permalink]))
