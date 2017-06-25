# Kiwi Web

## Local development

In emacs, run `M-x cljsbuild-start RET lein cljsbuild auto electron-dev RET` to start compilation for electron's Main process.

Then run `cider-jack-in-clojurescript` to run figwheel for electron's Renderer process.
