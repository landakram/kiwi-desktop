# Kiwi Desktop

## Local development

In emacs, run `M-x cljsbuild-start RET lein cljsbuild auto electron-dev electron-dev-tests electron-dev-devcards RET` to start compilation for electron's Main process.

Then run `cider-jack-in-clojurescript` to run figwheel-main for electron's Renderer process.

Then run `npx electron .` to start electron.

### Running tests

Tests are run in electron using [cljs-test-display](https://github.com/bhauman/cljs-test-display) and an [extra main](https://figwheel.org/docs/extra_mains.html) in figwheel.

After starting figwheel, open the test suite in electron by running: 

```sh
npx electron resources/main-tests.js
```

Tests are automatically re-run after every hot reload.

### Devcards

Devcards can also be used in development. 

After starting figwheel, open devcards in electron by running: 

```sh
npx electron resources/main-devcards.js
```

## Release builds

Compile for releases and create a built electron app with: 

```
npm run dist
```
