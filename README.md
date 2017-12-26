# Kiwi Desktop

## Local development

In emacs, run `M-x cljsbuild-start RET lein cljsbuild auto electron-dev RET` to start compilation for electron's Main process.

Then run `cider-jack-in-clojurescript` to run figwheel for electron's Renderer process.

Then run `electron .` to start electron.

## Release builds

Compile for releases and create a built electron app with: 

```
npm run dist
```
