(ns ^:figwheel-no-load kiwi.dev
  (:require [figwheel.client :as figwheel :include-macros true]
            [kiwi.core :as core]))

(enable-console-print!)

(figwheel/watch-and-reload
  :websocket-url "ws://localhost:3449/figwheel-ws"
  :jsload-callback core/render)

(core/init)
