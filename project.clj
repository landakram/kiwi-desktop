(defproject kiwi "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [ring-server "0.4.0"]
                 [reagent "0.6.0"]
                 [reagent-forms "0.5.11"]
                 [reagent-utils "0.1.5"]
                 [ring "1.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [prone "0.8.2"]
                 [compojure "1.4.0"]
                 [hiccup "1.0.5"]
                 [environ "1.0.1"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/clojurescript "1.9.671" :scope "provided"]
                 [kibu/pushy "0.3.3"]
                 [re-frame "0.9.4"]
                 [day8.re-frame/test "0.1.5"]
                 [re-com "1.0.0"]
                 [tailrecursion/cljson "1.0.7"]
                 [secretary "1.2.3"]
                 [com.andrewmcveigh/cljs-time "0.5.0"]
                 [binaryage/devtools "0.9.4"]]

  :plugins [[lein-environ "1.0.1"]
            [lein-cljsbuild "1.1.6"]
            [lein-figwheel "0.5.11"]
            [lein-asset-minifier "0.2.2"]]

  :min-lein-version "2.5.0"

  :clean-targets ^{:protect false} [:target-path
                                    "resources/main.js"
                                    "resources/public/js/app.js"
                                    "resources/public/js/app.js.map"
                                    "resources/public/js/out"
                                    "resources/public/js/app-release"
                                    "resources/public/js/electron-release"
                                    "resources/public/js/electron-dev"]

  :source-paths ["src/clj" "src/cljc" "src/cljs"]

  :minify-assets
  {:assets
   {"resources/public/css/site.min.css" "resources/public/css/site.css"}}

  :repl-options {:init-ns          kiwi.repl
                 :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  :cljsbuild {:builds [{:id           "dev"
                        :source-paths ["src/clj" "src/cljs/kiwi" "src/cljc" "env/dev/cljs"]
                        :figwheel     {:on-jsload "kiwi.core/render"}
                        :compiler     {:output-to     "resources/public/js/app.js"
                                       :output-dir    "resources/public/js/out"
                                       :asset-path    "js/out"
                                       :preloads [devtools.preload]
                                       :main          "kiwi.core"
                                       :optimizations :none
                                       :source-map    true
                                       :pretty-print  true}}
                       {:id "electron-dev"
                        :source-paths ["src/cljs/electron"]
                        :compiler {:output-to "resources/main.js"
                                   :output-dir "resources/public/js/electron-dev"
                                   :optimizations :simple
                                   :pretty-print true
                                   :cache-analysis true}}
                       {:id           "release"
                        :source-paths ["src/clj" "src/cljs/kiwi" "src/cljc"]
                        :compiler     {:output-to     "resources/public/js/app.js"
                                       :output-dir    "resources/public/js/app-release"
                                       :asset-path    "js/app-release"
                                       :main          "kiwi.core"
                                       :optimizations :advanced
                                       :cache-analysis true
                                       :infer-externs true
                                       :externs ["externs.js"]
                                       :source-map    "resources/public/js/app.js.map"
                                       :pretty-print  true}}
                       {:id "electron-release"
                        :source-paths ["src/cljs/electron"]
                        :compiler {:output-to "resources/main.js"
                                   :output-dir "resources/public/js/electron-release"
                                   :optimizations :advanced
                                   :pretty-print true
                                   :cache-analysis true
                                   :infer-externs true}}
                       ]
              }

  :figwheel {:http-server-root "public"
             :css-dirs         ["resources/public/css"]
             :server-port      3449}

  :profiles {:dev {:dependencies [[figwheel-sidecar "0.5.0-6"]
                                  [com.cemerick/piggieback "0.2.1"]]
                   :source-paths ["src/clj" "src/cljs" "src/cljc" "env/dev/cljs"]
                   :env          {:dev true}}})
