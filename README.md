# Kiwi Desktop

## Local development

In emacs, run `M-x cljsbuild-start RET lein cljsbuild auto electron-dev RET` to start compilation for electron's Main process.

Then run `cider-jack-in-clojurescript` to run figwheel for electron's Renderer process.

Then run `electron .` to start electron.

### Running tests

Tests can be run directly in the cljs repl or with devcards in electron.

#### CLJS repl

After starting the cljs repl, run the following: 

```cljs
cljs.user> (require 'kiwi.user)
cljs.user> (kiwi.tests/run)
```

#### Devcards

First ensure that `devcards-test` is being built by figwheel: 

```cljs
cljs.user> (start-autobuild :devcards-test)
```

Open the test suite in electron by running: 

```sh
electron resources/public/tests.html
```

## Release builds

Compile for releases and create a built electron app with: 

```
npm run dist
```

