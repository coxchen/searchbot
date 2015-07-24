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
