(ns searchbot.test-runner
  (:require
   [cljs.test :refer-macros [run-tests]]
   [searchbot.core-test]))

(enable-console-print!)

(defn runner []
  (if (cljs.test/successful?
       (run-tests
        'searchbot.core-test))
    0
    1))
