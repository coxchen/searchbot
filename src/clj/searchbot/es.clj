(ns searchbot.es
  (:require [clojurewerkz.elastisch.rest  :as esr]
            [clojurewerkz.elastisch.rest.document :as esd]))

(defn es-count
  [host idx idxType]
  (let [conn (esr/connect host)]
    (esd/count conn idx idxType)))

(defn es-search
  [host idx idxType query]
  (println "query")
  (println query)
  (let [conn (esr/connect host)]
    (esd/search conn idx idxType query)))
