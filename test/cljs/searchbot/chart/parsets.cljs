(ns searchbot.chart.parsets
  (:require-macros [cljs.test :refer (is deftest testing)])
  (:require [cljs.test]
            [searchbot.chart.parsets.agg :refer [agg->parsets]]))


(def sample-es-agg-resp
  {:took 40 :timed_out false :_shards {:total :3 :successful 3 :failed 0}
   :hits {:total 20465 :max_score 0.0 :hits []}
   :aggregations
   {:request {:doc_count_error_upper_bound 179 :sum_other_doc_count 11660
              :buckets [{:key "/favicon.ico" :doc_count 2445
                         :verb {:doc_count_error_upper_bound 0 :sum_other_doc_count 0
                                :buckets [{:key "get" :doc_count 2223
                                           :response {:doc_count_error_upper_bound 0 :sum_other_doc_count 0
                                                      :buckets [{:key "200" :doc_count 2201}
                                                                {:key "304" :doc_count 13}
                                                                {:key "206" :doc_count 9}]}}
                                          {:key "head" :doc_count 222
                                           :response {:doc_count_error_upper_bound 0 :sum_other_doc_count 0
                                                      :buckets [{:key "200" :doc_count 222}]}}]}}
                        {:key "/hackday08/randomtags.py" :doc_count 1935
                         :verb {:doc_count_error_upper_bound 0 :sum_other_doc_count 0
                                :buckets [{:key "post" :doc_count 1935
                                           :response {:doc_count_error_upper_bound 0 :sum_other_doc_count 0
                                                      :buckets [{:key "200" :doc_count 1934}
                                                                {:key "500" :doc_count 1}]}}]}}
                        {:key "/reset.css" :doc_count 1510
                         :verb {:doc_count_error_upper_bound 0 :sum_other_doc_count 0
                                :buckets [{:key "get" :doc_count 1510
                                           :response {:doc_count_error_upper_bound 0 :sum_other_doc_count 0
                                                      :buckets [{:key "200" :doc_count 1458}
                                                                {:key "304" :doc_count 52}]}}]}}
                        {:key "/style2.css" :doc_count 1497
                         :verb {:doc_count_error_upper_bound 0 :sum_other_doc_count 0
                                :buckets [{:key "get" :doc_count 1497
                                           :response {:doc_count_error_upper_bound 0 :sum_other_doc_count 0
                                                      :buckets [{:key "200" :doc_count 1449}
                                                                {:key "304" :doc_count 48}]}}]}}
                        {:key "/images/jordan-80.png" :doc_count 1413
                         :verb {:doc_count_error_upper_bound 0 :sum_other_doc_count 0
                                :buckets [{:key "get" :doc_count 1413
                                           :response {:doc_count_error_upper_bound 0 :sum_other_doc_count 0
                                                      :buckets [{:key "200" :doc_count 1392}
                                                                {:key "304" :doc_count 21}]}}]}}]}}})

(def sample-parsets-data
  [{:request "/favicon.ico", :verb "get", :response "200", :value 2201}
   {:request "/favicon.ico", :verb "get", :response "304", :value 13}
   {:request "/favicon.ico", :verb "get", :response "206", :value 9}
   {:request "/favicon.ico", :verb "head", :response "200", :value 222}
   {:request "/hackday08/randomtags.py", :verb "post", :response "200", :value 1934}
   {:request "/hackday08/randomtags.py", :verb "post", :response "500", :value 1}
   {:request "/reset.css", :verb "get", :response "200", :value 1458}
   {:request "/reset.css", :verb "get", :response "304", :value 52}
   {:request "/style2.css", :verb "get", :response "200", :value 1449}
   {:request "/style2.css", :verb "get", :response "304", :value 48}
   {:request "/images/jordan-80.png", :verb "get", :response "200", :value 1392}
   {:request "/images/jordan-80.png", :verb "get", :response "304", :value 21}])

(def sample-es-agg-resp-with-sub
  {:took 19 :timed_out false :_shards {:total 3 :successful 3 :failed 0}
   :hits {:total 20465 :max_score 0.0 :hits []}
   :aggregations
   {:request {:doc_count_error_upper_bound -1 :sum_other_doc_count 18904
              :buckets [{:key "/files/logstash/logstash-1.0.17-monolithic.jar" :doc_count 155
                         :verb {:doc_count_error_upper_bound 0 :sum_other_doc_count 0
                                :buckets [{:key "get" :doc_count 153
                                           :response {:doc_count_error_upper_bound 0 :sum_other_doc_count 0
                                                      :buckets [{:key "200" :doc_count 138 :sum_bytes {:value 3.114356684E9}}
                                                                {:key "206" :doc_count 14 :sum_bytes {:value 3.5668889E7}}
                                                                {:key "304" :doc_count 1 :sum_bytes {:value 193.0}}]}
                                           :sum_bytes {:value 3.150025766E9}}
                                          {:key "head" :doc_count 2
                                           :response {:doc_count_error_upper_bound 0 :sum_other_doc_count 0
                                                      :buckets [{:key "200" :doc_count 2 :sum_bytes {:value 634.0}}]}
                                           :sum_bytes {:value 634.0}}]}
                         :sum_bytes {:value 3.1500264E9}}
                        {:key "/images/web/2009/banner.png" :doc_count 1398
                         :verb {:doc_count_error_upper_bound 0 :sum_other_doc_count 0
                                :buckets [{:key "get" :doc_count 1398
                                           :response {:doc_count_error_upper_bound 0 :sum_other_doc_count 0
                                                      :buckets [{:key "200" :doc_count 1382 :sum_bytes {:value 7.2668621E7}}
                                                                {:key "304" :doc_count 16 :sum_bytes {:value 2987.0}}]}
                                           :sum_bytes {:value 7.2671608E7}}]}
                         :sum_bytes {:value 7.2671608E7}}
                        {:key "/files/logstash/logstash-1.0.12-monolithic.jar" :doc_count 1
                         :verb {:doc_count_error_upper_bound 0 :sum_other_doc_count 0
                                :buckets [{:key "get" :doc_count 1
                                           :response {:doc_count_error_upper_bound 0 :sum_other_doc_count 0
                                                      :buckets [{:key "200" :doc_count 1 :sum_bytes {:value 3.7853629E7}}]}
                                           :sum_bytes {:value 3.7853629E7}}]}
                         :sum_bytes {:value 3.7853629E7}}
                        {:key "/files/logstash/logstash-1.0.14-monolithic.jar" :doc_count 1
                         :verb {:doc_count_error_upper_bound 0 :sum_other_doc_count 0
                                :buckets [{:key "get" :doc_count 1
                                           :response {:doc_count_error_upper_bound 0 :sum_other_doc_count 0
                                                      :buckets [{:key "200" :doc_count 1 :sum_bytes {:value 3.4408788E7}}]}
                                           :sum_bytes {:value 3.4408788E7}}]}
                         :sum_bytes {:value 3.4408788E7}}
                        {:key "/files/logstash/logstash-1.0.6-monolithic.jar" :doc_count 1
                         :verb {:doc_count_error_upper_bound 0 :sum_other_doc_count 0
                                :buckets [{:key "get" :doc_count 1
                                           :response {:doc_count_error_upper_bound 0 :sum_other_doc_count 0
                                                      :buckets [{:key "200" :doc_count 1 :sum_bytes {:value 3.382643E7}}]}
                                           :sum_bytes {:value 3.382643E7}}]}
                         :sum_bytes {:value 3.382643E7}}]}
    :sum_bytes {:value 3.541888535E9}}})

(def sample-parsets-data-with-sub
  [{:request "/files/logstash/logstash-1.0.17-monolithic.jar", :verb "get", :response "200", :value 3114356684}
   {:request "/files/logstash/logstash-1.0.17-monolithic.jar", :verb "get", :response "206", :value 35668889}
   {:request "/files/logstash/logstash-1.0.17-monolithic.jar", :verb "get", :response "304", :value 193}
   {:request "/files/logstash/logstash-1.0.17-monolithic.jar", :verb "head", :response "200", :value 634}
   {:request "/images/web/2009/banner.png", :verb "get", :response "200", :value 72668621}
   {:request "/images/web/2009/banner.png", :verb "get", :response "304", :value 2987}
   {:request "/files/logstash/logstash-1.0.12-monolithic.jar", :verb "get", :response "200", :value 37853629}
   {:request "/files/logstash/logstash-1.0.14-monolithic.jar", :verb "get", :response "200", :value 34408788}
   {:request "/files/logstash/logstash-1.0.6-monolithic.jar", :verb "get", :response "200", :value 33826430}])

(deftest handle-agg-resp-test
  (is (= sample-parsets-data
         (agg->parsets (:aggregations sample-es-agg-resp) [:request :verb :response] [:doc_count]))))

(deftest handle-agg-resp-with-sub-test
  (is (= sample-parsets-data-with-sub
         (agg->parsets (:aggregations sample-es-agg-resp-with-sub) [:request :verb :response] [:sum_bytes :value]))))
