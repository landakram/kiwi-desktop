(ns reagent-sample.macros)

(defmacro <? [ch]
  `(reagent-sample.sync/throw-error (cljs.core.async/<!  ~ch)))
