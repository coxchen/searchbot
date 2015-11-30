(ns searchbot.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [searchbot.core-test]
            [searchbot.chart.parsets]))

(defn -main [& args]
  (println "Doo Test Runner"))

(set! *main-cli-fn* -main)

(doo-tests 'searchbot.core-test
           'searchbot.chart.parsets)
