(ns searchbot.es
  (:require [clojurewerkz.elastisch.rest  :as esr]
            [clojurewerkz.elastisch.rest.document :as esd]))

(defn es-count
  [host idx idxType]
  (let [conn (esr/connect host)]
    (esd/count conn idx idxType)))

(defn es-search
  [host idx idxType query]
  (let [conn (esr/connect host)
        resp (esd/search conn idx idxType query)]
    resp))
