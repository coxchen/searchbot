# searchbot

A data-driven prototyping engine for dashboards based on ElasticSearch

![app](doc/app.png)

## Dashboard Definition

![app definition](doc/app_def.png)

## Development

Enter Clojure REPL `lein repl`

```clojure
(run)
(browser-repl)
```

In the REPL, type

```clojure
(ns searchbot.core)
(swap! app-state assoc :text "Interactivity FTW")
```

## Running with Foreman

``` sh
lein with-profile -dev,+production uberjar && foreman start
```

Now your app is running at
[http://localhost:5000](http://localhost:5000) in production mode.


## Chestnut

Created with [Chestnut](http://plexus.github.io/chestnut/) 0.7.0.
