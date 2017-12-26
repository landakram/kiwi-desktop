# Kiwi Desktop

## Local development

In emacs, run `M-x cljsbuild-start RET lein cljsbuild auto electron-dev RET` to start compilation for electron's Main process.

Then run `cider-jack-in-clojurescript` to run figwheel for electron's Renderer process.

Then run `electron .` to start electron.

### Running tests

After starting the cljs repl, run the following: 

```cljs
cljs.user> (require 'kiwi.user)
cljs.user> (kiwi.runner/run)
```

## Release builds

Compile for releases and create a built electron app with: 

```
npm run dist
```

