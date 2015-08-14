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
  (let [took (:took resp)
        top-agg-key (-> resp :aggregations keys first)
        top-agg-buckets (-> resp :aggregations top-agg-key :buckets)
        top-agg (->> top-agg-buckets (map (juxt :key :doc_count)) flatten)
        hits (-> resp :hits :total)
        shards ((juxt :total :successful :failed) (:_shards resp))]
    (println "[now]" (java.util.Date.)
             "[resp]" top-agg-key
             "[took]" took "ms"
             "[shards]" shards
             "[hits]" hits
             "[agg]" top-agg)
    ))

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
