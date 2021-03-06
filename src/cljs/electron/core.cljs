(ns electron.core)

(set! *warn-on-infer* true)

(def electron       (js/require "electron"))
(def open (js/require "open"))
(def app            (.-app electron))
(def shell (.-shell electron))
(def Menu (.-Menu electron))
(def browser-window (.-BrowserWindow electron))
(def context-menu (js/require "electron-context-menu"))
(context-menu (clj->js {}))
(def default-menu (js/require "electron-default-menu"))

(def electron-debug (js/require "electron-debug"))
(electron-debug (clj->js { :enabled true }))
(def main-window (atom nil))

(goog-define start-page "/public/index.html#/page/home")

(defn handle-swipe [e direction]
  (let [web-contents (.-webContents ^js/electron.BrowserWindow @main-window)]
    (cond 
      (= direction "left")
      (when (.canGoBack web-contents)
        (.goBack web-contents))
      (= direction "right")
      (when (.canGoForward web-contents)
        (.goForward web-contents)))))

(defn init-browser []
  (let [menu (default-menu app shell)] 
       (.setApplicationMenu Menu (.buildFromTemplate Menu menu))

       (reset! main-window (browser-window.
                            (clj->js {:width 800
                                      :height 800
                                      :webPreferences
                                      {:nodeIntegration true}})))

                                        ; Path is relative to the compiled js file (main.js in our case)
       (.loadURL ^js/electron.BrowserWindow @main-window (str "file://" js/__dirname start-page))
       (.on ^js/electron.BrowserWindow @main-window "swipe" handle-swipe)

       #_ (.openDevTools (.-webContents ^js/electron.BrowserWindow @main-window))

       (.on ^js/electron.WebContents (.-webContents ^js/electron.BrowserWindow @main-window)
            "new-window" (fn [event url]
                           (.preventDefault event)
                           (open url)))

       (.on ^js/electron.BrowserWindow @main-window
            "closed" #(reset! main-window nil))))

(.on app "window-all-closed" #(when-not (= js/process.platform "darwin")
                                (.quit app)))

(.on app "ready" init-browser)

(.on app "open-url" (fn [e url]
                      (.preventDefault e)
                      (.executeJavascript
                       (.-webContents @main-window)
                       (str "console.log('" url "')"))
                      (js/console.log url)))


