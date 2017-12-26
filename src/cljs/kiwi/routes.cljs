(ns kiwi.routes
  (:require [secretary.core :as secretary :include-macros true]
            [kiwi.page :as page]
            [re-frame.core :as re-frame :refer [after dispatch dispatch-sync enrich path register-handler register-sub subscribe]]
            [kiwi.db :as page-db])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]
   [reagent.ratom :refer [reaction]]))

(def dispatch! secretary/dispatch!)

(secretary/set-config! :prefix "#")

(secretary/defroute index-route "/" []
  (dispatch [:navigate [:home-page]]))

(secretary/defroute settings-route "/settings" []
  (dispatch [:navigate [:settings-page]]))

(defn dispatch-search-page [filter]
  (go
    (let [pages (<! (page-db/load-all!))]
      (dispatch [:navigate [:search-page]
                 {:pages pages
                  :filter filter}]))))

(secretary/defroute search-route "/search" [_ query-params]
  (dispatch-search-page (get query-params :filter "")))

(secretary/defroute page-route "/page/:permalink" [permalink]
  (go
    (let [maybe-page (<! (page-db/load permalink))
          page (if (= maybe-page :not-found) 
                 (page/new-page permalink) 
                 maybe-page)
          permalinks (<! (page-db/load-permalinks))]
      (dispatch [:navigate [:wiki-page-view permalink]
                 {:page page :permalinks permalinks :editing? false}]))))
