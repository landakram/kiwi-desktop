(ns reagent-sample.handler
  (:require [compojure.core :refer [GET ANY defroutes]]
            [compojure.route :refer [not-found resources]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [include-js include-css]]
            [prone.middleware :refer [wrap-exceptions]]
            [ring.middleware.reload :refer [wrap-reload]]
            [environ.core :refer [env]]))

(def home-page
  (html
   [:html
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport"
             :content "width=device-width, initial-scale=1"}]
     [:script {:src "https://use.typekit.net/ues0olh.js"}]
     [:script "try{Typekit.load({ async: false });}catch(e){}"]
     (include-css "//cdnjs.cloudflare.com/ajax/libs/highlight.js/8.6/styles/default.min.css")
     (include-css "//cdnjs.cloudflare.com/ajax/libs/codemirror/5.7.0/codemirror.css")
     (include-css "/css/tomorrow-night-eighties.css")
     (include-css "/css/editor.css")
     (include-css (if (env :dev) "/css/styles.css" "/css/styles.min.css"))]
    [:body
      [:div.body {:id "app"}
       [:div
       [:div.header ""]
       [:section.content-wrapper
        [:div.content
            [:article#page ""]]]]]
     (include-js "//cdnjs.cloudflare.com/ajax/libs/dropbox.js/0.10.3/dropbox.js")
     (include-js "//cdnjs.cloudflare.com/ajax/libs/marked/0.3.2/marked.min.js")
     (include-js "//cdnjs.cloudflare.com/ajax/libs/highlight.js/8.6/highlight.min.js")
     (include-js "//cdnjs.cloudflare.com/ajax/libs/codemirror/5.7.0/codemirror.js")
     (include-js "//cdnjs.cloudflare.com/ajax/libs/codemirror/5.7.0/addon/mode/overlay.min.js")
     (include-js "//cdnjs.cloudflare.com/ajax/libs/codemirror/5.7.0/mode/markdown/markdown.js")
     (include-js "//cdnjs.cloudflare.com/ajax/libs/codemirror/5.7.0/mode/gfm/gfm.js")
     (include-js "/js/app.js")]]))

(defroutes routes
  (ANY "*" [] home-page)
  (resources "/")
  (not-found "Not Found"))

(def app
  (let [handler (wrap-defaults #'routes site-defaults)]
    (if (env :dev) (-> handler wrap-exceptions wrap-reload) handler)))
