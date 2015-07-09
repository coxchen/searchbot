(ns searchbot.es
  (:require [clojurewerkz.elastisch.rest  :as esr]
            [clojurewerkz.elastisch.rest.document :as esd]))

(defn es-count
  ([host idx]
   (es-count host idx nil))
  ([host idx idxType]
   (let [conn (esr/connect host)]
     (esd/count conn idx idxType))))

(defn- pr-resp
  [resp]
  (println "[resp]" (-> resp :aggregations keys first) "took" (:took resp) "ms"))

(defn es-search
  ([host idx query]
   (let [conn (esr/connect host)
         resp (esd/search-all-types conn idx query)]
     (pr-resp resp)
     resp))
  ([host idx idxType query]
   (let [conn (esr/connect host)
         resp (esd/search conn idx idxType query)]
     (pr-resp resp)
     resp)))
