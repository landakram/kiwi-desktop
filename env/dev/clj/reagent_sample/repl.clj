(ns reagent-sample.repl
  (:use reagent-sample.handler
        ring.server.standalone
        [ring.middleware file-info file])
  (:require [figwheel-sidecar.repl-api :as repl-api]))

(defonce server (atom nil))

(defn get-handler []
  ;; #'app expands to (var app) so that when we reload our code,
  ;; the server is forced to re-resolve the symbol in the var
  ;; rather than having its own copy. When the root binding
  ;; changes, the server picks it up without having to restart.
  (-> #'app
      ; Makes static assets in $PROJECT_DIR/resources/public/ available.
      (wrap-file "resources")
      ; Content-Type, Content-Length, and Last Modified headers for files in body
      (wrap-file-info)))

(defn start-server
  "used for starting the server in development mode from REPL"
  [& [port]]
  (let [port (if port (Integer/parseInt port) 3000)]
    (reset! server
            (serve (get-handler)
                   {:port port
                    :auto-reload? true
                    :join? false}))
    (println (str "You can view the site at http://localhost:" port))))

(defn stop-server []
  (.stop @server)
  (reset! server nil))

(def figwheel-config
  {:figwheel-options {:http-server-root "public"
                              :server-port 3449
                              :nrepl-port 7002
                              :css-dirs ["resources/public/css"]
                              :ring-handler reagent-sample.handler/app}
   :build-ids ["dev"]
   :all-builds
   []})

(defn start []
  (repl-api/start-figwheel! figwheel-config))

(defn cljs []
  (repl-api/cljs-repl))
