(ns searchbot.chart.parsets.agg)

;;;;;;;;;;;;;;;;;
;; make agg query

(defn- build-sub-aggs [sub-aggs]
  (if sub-aggs {:aggs sub-aggs} {}))

(defn- sort-sub [sub-aggs]
  (if-let [sort-field (-> sub-aggs keys first)]
    {:order {sort-field "desc"}}
    {}))

(defn build-parsets-agg [{:keys [terms sub-aggs size] :or {size 5}}]
  (reduce (fn [agg-query a-term]
            {:aggregations
             (merge {(keyword a-term)
                     (merge {:terms
                             (merge {:field a-term :size size}
                                    (sort-sub sub-aggs))}
                            agg-query)}
                    sub-aggs)})
          (build-sub-aggs sub-aggs) terms))

;;;;;;;;;;;;;;;;;;;;;;
;; handle agg response

(defn- walk-buckets [de-buck bucket prefix steps value-path]
  (map #(de-buck % de-buck prefix (first steps) (rest steps) value-path)
       (get-in bucket [(first steps) :buckets])))

(defn- <-buckets [bucket de-buck prefix current-step steps value-path]
  (if (= 0 (count steps))
    (merge prefix {current-step (:key bucket) :value (get-in bucket value-path)})
    (let [prefix (merge prefix {current-step (:key bucket)})]
      (walk-buckets de-buck bucket prefix steps value-path))))

(defn agg->parsets [agg-result steps value-path]
  (vec (flatten (walk-buckets <-buckets agg-result {} steps value-path))))
