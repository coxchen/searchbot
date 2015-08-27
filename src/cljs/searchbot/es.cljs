(ns searchbot.es
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [put! <! >! chan timeout]]))

(defn es-count [api-url]
  (let [c (chan)]
    (go (let [{{docCount :count} :body} (<! (http/get api-url))]
          (>! c docCount)))
    c))

(defn es-query [api-url post-body]
  (let [c (chan)]
    (go (let [{resp :body} (<! (http/post api-url {:json-params (-> post-body)}))]
          (>! c resp)))
    c))

(defn es-agg [api-url post-body]
  (let [c (chan)]
    (go (let [{resp :body} (<! (http/post api-url
                                         {:json-params
                                          (-> post-body
                                              (assoc :search_type "count")
                                              (assoc :preference "_local")
                                              )}))]
          (>! c resp)))
    c))


(defn filtered-agg [agg-job]
  (let [match-all-query {:query {:match_all {}}}
        query (if-let [query-filter (or (-> agg-job :filter) (-> agg-job :default :filter))]
                {:query {:filtered (assoc match-all-query :filter query-filter)}}
                match-all-query)]
    (-> query (merge (:body agg-job)))))

;;;;;;;;;;;;;;;;;;;;;
;; handle agg buckets

(defn extract-view [agg-data]
  (fn [view]
    (if (map? (get agg-data view))
      (if-let [inner-bucks (get-in agg-data [view :buckets])]
        (into {} (map (fn [inner] {(keyword (:key inner)) (:doc_count inner)}) inner-bucks))
        {view (get-in agg-data [view :value])})
      (select-keys agg-data [view]))))


(defn agg-val [agg-data views]
  (let [result (reduce merge (map (extract-view agg-data) views))]
    result))

(defn flatten-agg-buckets [agg-view agg-buckets]
  (map (fn [bucket]
         (agg-val bucket agg-view))
       agg-buckets))

(defn get-agg-buckets [cursor bucket-path]
  (-> cursor (get-in bucket-path) seq))
