(ns kiwi.keybindings
  (:require
   [re-frame.core :as re-frame :refer [after dispatch dispatch-sync enrich path register-handler register-sub subscribe]]

   [kiwi.routes :as routes]))

(def mousetrap (js/require "mousetrap"))

(defn- toggle-editing! []
  (dispatch [:assoc-editing? (not @(subscribe [:editing?]))]))

(defn- escape! []
  (when @(subscribe [:editing?])
    (toggle-editing!))
  (.blur js/document.activeElement)
  (dispatch [:hide-modal]))

(def scroll-by 50)

(def page-scroll-by 300)

(def keybindings
  [{:key "e"
    :keymap :local
    :handler #(when (not @(subscribe [:editing?]))
                (toggle-editing!))}
   {:key "i"
    :keymap :local
    :handler #(when (not @(subscribe [:editing?]))
                (toggle-editing!))}

   {:key "command+enter"
    :keymap :global
    :handler toggle-editing!}
   {:key "g s"
    :keymap :local
    :handler #(dispatch [:set-route (routes/search-route)])}
   {:key "g h"
    :keymap :local
    :handler #(dispatch [:set-route (routes/page-route {:permalink "home"})])}
   {:key "n"
    :keymap :local
    :handler #(dispatch [:show-modal :add-page])}
   {:key "esc"
    :keymap :global
    :handler escape!}
   {:key "ctrl+["
    :keymap :global
    :handler escape!}
   {:key "u"
    :keymap :local
    :handler #(.scrollBy js/window 0 (- page-scroll-by))}
   {:key "d"
    :keymap :local
    :handler #(.scrollBy js/window 0 page-scroll-by)}
   {:key "j"
    :keymap :local
    :handler #(.scrollBy js/window 0 scroll-by)}
   {:key "k"
    :keymap :local
    :handler #(.scrollBy js/window 0 (- scroll-by))}
   {:key "H"
    :keymap :local
    :handler #(.back js/window.history)}
   {:key "L"
    :keymap :local
    :handler #(.forward js/window.history)}])

(defn register-keybindings! []
  "Register some vim-esque keybindings for navigation"
  (doseq [{:keys [key keymap handler] :as binding} keybindings]
    (cond
      (= keymap :local) (.bind mousetrap key handler)
      (= keymap :global) (.bindGlobal mousetrap key handler))))
