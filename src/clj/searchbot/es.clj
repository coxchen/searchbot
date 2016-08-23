(ns searchbot.es
  (:require [clojurewerkz.elastisch.rest  :as esr]
            [clojurewerkz.elastisch.native :as esn]
            [clojurewerkz.elastisch.rest.document :as esdr]
            [clojurewerkz.elastisch.native.document :as esdn]))

(def ES_MODE (ref :REST))

(defn connect-es-native [server]
  (let [es-ip-address   (get-in server [:es :host])
        es-port         (get-in server [:es :port])
        es-cluster-name (get-in server [:es :cluster])]
    (esn/connect [[es-ip-address es-port]] {"cluster.name" es-cluster-name})))

(defn connect-es-rest [server]
  (let [host (str "http://" (get-in server [:es :host]) ":" (get-in server [:es :port]))]
    (esr/connect host)))

(defn do-connect [server]
  (if (= :REST @ES_MODE)
    (connect-es-rest server)
    (connect-es-native server)))

(defn do-count [conn idx idxType]
  (if (= :REST @ES_MODE)
    (esdr/count conn idx idxType)
    (esdn/count conn idx idxType)))

(defn- pr-query [query]
  (clojure.pprint/pprint query)
  query)

(defn do-search [conn idx idxType query]
  (let [query (pr-query query)]
    (if (= :REST @ES_MODE)
      (esdr/search conn idx idxType query)
      (esdn/search conn idx idxType query))))

(defn do-search-all-types [conn idx query]
  (let [query (pr-query query)]
    (if (= :REST @ES_MODE)
      (esdr/search-all-types conn idx query)
      (esdn/search-all-types conn idx query))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- pr-resp [resp]
  (let [_ (println "##########")
        _ (println resp)
        _ (println "##########")
        took            (:took resp)
        ;top-agg-key     (-> resp :aggregations keys first)
        ;top-agg-buckets (-> resp :aggregations top-agg-key :buckets)
        ;top-agg         (->> top-agg-buckets (map (juxt :key :doc_count)) flatten)
        hits            (-> resp :hits :total)
        shards          ((juxt :total :successful :failed) (:_shards resp))]
    (println "[now]" (java.util.Date.)
             ;"[resp]" top-agg-key
             "[took]" took "ms"
             "[shards]" shards
             "[hits]" hits
             ;"[agg]" top-agg
             )
    ))


(defn es-count
  ([host idx]
   (es-count host idx nil))
  ([host idx idxType]
   (let [conn (do-connect host)
         resp (do-count conn idx idxType)
         _ (.close conn)]
     resp)))

(defn es-search
  ([host idx query]
   (let [conn (do-connect host)
         resp (do-search-all-types conn idx query)
         _ (.close conn)]
     (pr-resp resp)
     resp))
  ([host idx idxType query]
   (let [conn (do-connect host)
         resp (do-search conn idx idxType query)
         _ (.close conn)]
     (pr-resp resp)
     resp)))
