(ns kiwi.macros)

(defmacro <? [ch]
  `(kiwi.sync/throw-error (cljs.core.async/<!  ~ch)))
