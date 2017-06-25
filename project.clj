(defproject reagent-sample "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [ring-server "0.4.0"]
                 [reagent "0.5.1"]
                 [reagent-forms "0.5.11"]
                 [reagent-utils "0.1.5"]
                 [ring "1.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [prone "0.8.2"]
                 [compojure "1.4.0"]
                 [hiccup "1.0.5"]
                 [environ "1.0.1"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/clojurescript "1.7.122" :scope "provided"]
                 [kibu/pushy "0.3.3"]
                 [re-frame "0.5.0-alpha1"]
                 [re-com "1.0.0"]
                 [tailrecursion/cljson "1.0.7"]
                 [secretary "1.2.3"]
                 [cljsjs/dexie "1.2.0-1"]
                 [com.andrewmcveigh/cljs-time "0.3.14"]]

  :plugins [[lein-environ "1.0.1"]
            [lein-cljsbuild "1.1.3"]
            [lein-figwheel "0.5.8"]
            [lein-asset-minifier "0.2.2"]]

  :min-lein-version "2.5.0"

  :clean-targets ^{:protect false} [:target-path
                                    [:cljsbuild :builds :app :compiler :output-dir]
                                    [:cljsbuild :builds :app :compiler :output-to]]

  :source-paths ["src/clj" "src/cljc" "src/cljs"]

  :minify-assets
  {:assets
   {"resources/public/css/site.min.css" "resources/public/css/site.css"}}

  :repl-options {:init-ns          reagent-sample.repl
                 :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  :cljsbuild {:builds [{:id           "dev"
                        :source-paths ["src/clj" "src/cljs/reagent_sample" "src/cljc" "env/dev/cljs"]
                        :figwheel     {:on-jsload "reagent-sample.core/render"}
                        :compiler     {:output-to     "resources/public/js/app.js"
                                       :output-dir    "resources/public/js/out"
                                       :asset-path    "js/out"
                                       :main          reagent-sample.core
                                       :optimizations :none
                                       :source-map    true
                                       :pretty-print  true}}
                       {:id "electron-dev"
                        :source-paths ["src/cljs/electron"]
                        :compiler {:output-to "resources/main.js"
                                   :output-dir "resources/public/js/electron-dev"
                                   :optimizations :simple
                                   :pretty-print true
                                   :cache-analysis true}}]}

  :figwheel {:http-server-root "public"
             :css-dirs         ["resources/public/css"]
             :server-port      3449}

  :profiles {:dev {:dependencies [[figwheel-sidecar "0.5.0-6"]
                                  [com.cemerick/piggieback "0.2.1"]]
                   :source-paths ["src/clj" "src/cljs" "src/cljc" "env/dev/cljs"]
                   :env          {:dev true}}})
