(defproject searchbot "0.2.4"
  :description "Searchbot for ES"
  :url "http://github.com/coxchen/searchbot"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src/clj"]

  :test-paths ["test/clj"]

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.122"]
                 [devcards "0.2.1"]
                 [ring "1.4.0"]
                 [ring/ring-core "1.4.0"]
                 [ring/ring-json "0.4.0"]
                 [ring-json-response "0.2.0"]
                 [ring/ring-defaults "0.1.5"]
                 [compojure "1.4.0"]
                 [enlive "1.1.6"]
                 [org.omcljs/om "0.9.0"]
                 [environ "1.0.1"]
                 [http-kit "2.1.19"]
                 [prismatic/om-tools "0.4.0"]
                 [sablono "0.3.6"]
                 [cljs-http "0.1.37"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [clojurewerkz/elastisch "2.2.2"]
                 [org.clojure/tools.cli "0.3.3"]
                 [cheshire "5.6.3"]]

  :plugins [[lein-cljsbuild "1.0.5"]
            [lein-environ "1.0.0"]
            [lein-pprint "1.1.1"]
            [lein-ancient "0.6.7"]
            [lein-doo "0.1.4"]]

  :min-lein-version "2.5.0"

  :clean-targets [:target-path :compile-path "out"]

  :cljsbuild
  {:builds
   {:app {:source-paths ["src/cljs"]
          :compiler {:output-to     "resources/public/js/app.js"
                     :output-dir    "resources/public/js/out"
                     :source-map    "resources/public/js/out.js.map"
                     :optimizations :none
                     :pretty-print  true}}
    :devcards {:source-paths ["src/cljs"]
               :figwheel { :devcards true }
               :compiler { :main    "searchbot.devcards"
                           :asset-path "js/devcards_out"
                           :source-map "resources/public/js/devcards_out/showdown.js.map"
                           :output-to  "resources/public/js/searchbot_devcards.js"
                           :output-dir "resources/public/js/devcards_out"
                           :source-map-timestamp true
                           :optimizations :none
                           :pretty-print  true }}
    }}

  :doo {:paths {:karma "karma"}}

  :profiles {:dev {:source-paths ["env/dev/clj"]
                   :test-paths ["test/clj"]

                   :dependencies [[figwheel "0.4.0"]
                                  [figwheel-sidecar "0.4.0"]
                                  [com.cemerick/piggieback "0.2.1"]
                                  [org.clojure/tools.nrepl "0.2.11"]
                                  [weasel "0.7.0"]]

                   :repl-options {:init-ns searchbot.server
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

                   :plugins [[lein-figwheel "0.4.0"]]

                   :figwheel {:http-server-root "public"
                              :server-port 3449
                              :css-dirs ["resources/public/css"]
                              :ring-handler searchbot.server/http-handler}

                   :env {:is-dev true}

                   :cljsbuild {:test-commands { "test" ["phantomjs" "env/test/js/unit-test.js" "env/test/unit-test.html"] }
                               :builds {:app {:source-paths ["env/dev/cljs"]}
                                        :test {:source-paths ["src/cljs" "test/cljs"]
                                               :compiler {:output-to     "resources/public/js/app_test.js"
                                                          :output-dir    "resources/public/js/test"
                                                          :source-map    "resources/public/js/test.js.map"
                                                          :optimizations :whitespace
                                                          :pretty-print  false}}
                                        :browser-test {:source-paths ["src/cljs" "test/cljs"]
                                                       :compiler {:output-to "out/browser_tests.js"
                                                                  :main "searchbot.doo-runner"
                                                                  :optimizations :none}}
                                        :node-test {:source-paths ["src/cljs" "test/cljs"]
                                                    :compiler {:output-to "out/node_tests.js"
                                                               :output-dir "out"
                                                               :main "searchbot.doo-runner"
                                                               :optimizations :none
                                                               :hashbang false
                                                               :target :nodejs}}}}}

             :uberjar {:source-paths ["env/prod/clj"]
                       :hooks [leiningen.cljsbuild]
                       :env {:production true}
                       :omit-source true
                       :aot :all
                       :main searchbot.server
                       :cljsbuild {:builds {:app
                                            {:source-paths ["env/prod/cljs"]
                                             :compiler
                                             {:optimizations :whitespace ;:advanced
                                              :pretty-print true ;false
                                              }}}}}}
  )
