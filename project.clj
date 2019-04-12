(defproject kiwi "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.10.0"]
                 [reagent "0.8.1"]
                 [reagent-forms "0.5.11"]
                 [reagent-utils "0.1.5"]
                 [hiccup "1.0.5"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/clojurescript "1.10.339" :scope "provided"]
                 [kibu/pushy "0.3.3"]
                 [re-frame "0.10.6"]
                 [day8.re-frame/test "0.1.5"]
                 [re-com "1.0.0"]
                 [secretary "1.2.3"]
                 [devcards "0.2.6"]
                 [com.andrewmcveigh/cljs-time "0.5.0"]
                 [binaryage/devtools "0.9.4"]]

  :plugins [[lein-cljsbuild "1.1.6"]
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

  :source-paths ["src" "test" "cards"]
  :test-paths ["test/cljs"]

  :minify-assets
  {:assets
   {"resources/public/css/site.min.css" "resources/public/css/site.css"}}

  :aliases
  {"fig" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev"]}

  :cljsbuild {:builds [{:id "electron-dev"
                        :source-paths ["src/cljs/electron"]
                        :compiler {:output-to "resources/main.js"
                                   :output-dir "resources/public/js/electron-dev"
                                   :optimizations :simple
                                   :pretty-print true
                                   :cache-analysis true}}
                       {:id           "release"
                        :source-paths ["src/cljs/kiwi"]
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
                                   :infer-externs true}}]}

  :profiles {:dev {:dependencies [[cider/piggieback "0.3.1"]
                                  [com.bhauman/figwheel-main "0.2.0"]
                                  [com.bhauman/cljs-test-display "0.1.1"]
                                  [day8.re-frame/re-frame-10x "0.4.0"]]
                   :source-paths ["src/cljs"]
                   :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}}})
