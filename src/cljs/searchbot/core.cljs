(ns searchbot.core
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [om.core :as om :include-macros true]
;;             [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [cljs-http.client :as http]
            [goog.events :as events]
            [cljs.core.async :refer [put! <! >! chan timeout]]
            [searchbot.widgets :refer [header agg-summary agg-table chart es-chart aggregator]]))

(defonce app-state (atom {:header-text "AVC realtime aggregation"
                          :avc-count 0
                          :agg {:div {:width "90%" :height 300}}
                          }))

(defcomponent my-app [app owner]
  (render [this] (html [:div
                        (om/build header app)
                        (om/build aggregator app
                                  {:opts {:agg-key "SSID AGG"
                                          :body {:aggs {:ssids
                                                        {:terms {:field "ssid" :order {:sum_usage "desc"}}
                                                         :aggs {:sum_usage {:sum {:field "usage"}}
                                                                :client_count
                                                                {:cardinality {:field "client_mac.hash"
                                                                               :precision_threshold 50000}}}}}}}})
                        (om/build aggregator app
                                  {:opts {:agg-key "APP AGG"
                                          :body {:aggs {:apps
                                                        {:terms {:field "app" :order {:sum_usage "desc"}}
                                                         :aggs {:sum_usage {:sum {:field "usage"}}
                                                                :sum_up {:sum {:field "up"}}
                                                                :sum_down {:sum {:field "down"}}}}}}}})
                        (om/build aggregator app
                                  {:opts {:agg-key "TIME AGG"
                                          :body {:aggs {:traffic_over_time
                                                        {:date_histogram
                                                         {:field "timestamp" :interval "1h"
                                                          :format "MM-dd kk:mm" :post_zone "+08:00"}
                                                         :aggs {:sum_up {:sum {:field "up"}}
                                                                :sum_down {:sum {:field "down"}}}}}}}})
                        [:.row [:.col-lg-4 (om/build agg-summary app)]]
                        [:.row
                         [:.col-lg-3
                          (om/build agg-table app
                                    {:opts {:agg-key "SSID AGG"
                                            :agg-top "ssids"
                                            :header [{:label "SSID" :agg :key}
                                                     {:label "Client Count" :agg :client_count}
                                                     {:label "Usage" :agg :sum_usage}]}})]
                         [:.col-lg-3
                          (om/build agg-table app
                                    {:opts {:agg-key "APP AGG"
                                            :agg-top "apps"
                                            :header [{:label "Application" :agg :key}
                                                     {:label "Usage" :agg :sum_usage}
                                                     {:label "UpLink" :agg :sum_up}
                                                     {:label "DownLink" :agg :sum_down}]}})]
                         [:.col-lg-3
                          (om/build agg-table app
                                    {:opts {:agg-key "TIME AGG"
                                            :agg-top "traffic_over_time"
                                            :header [{:label "TIME" :agg :key_as_string}
                                                     {:label "UpLink" :agg :sum_up}
                                                     {:label "DownLink" :agg :sum_down}]}})]
                         ]
                        [:.row
                         [:.col-lg-3
                          (om/build es-chart (:agg app)
                                    {:opts {:id "ssid_pie"
                                            :agg-key "SSID AGG"
                                            :agg-top "ssids"
                                            :agg-view :sum_usage
                                            :chart {:bounds {:x "5%" :y "15%" :width "80%" :height "80%"}
                                                    :plot js/dimple.plot.pie
                                                    :p-axis "sum_usage"
                                                    :c-axis "key"}
                                            :draw-fn :draw-ring}})]
                         [:.col-lg-3
                          (om/build es-chart (:agg app)
                                    {:opts {:id "app_pie"
                                            :agg-key "APP AGG"
                                            :agg-top "apps"
                                            :agg-view :sum_usage
                                            :chart {:bounds {:x "5%" :y "15%" :width "80%" :height "80%"}
                                                    :plot js/dimple.plot.pie
                                                    :p-axis "sum_usage"
                                                    :c-axis "key"}
                                            :draw-fn :draw-ring}})]
                         [:.col-lg-3
                          (om/build es-chart (:agg app)
                                    {:opts {:id "time_line"
                                            :agg-key "TIME AGG"
                                            :agg-top "traffic_over_time"
                                            :agg-view :sum_up
                                            :draw-fn :draw-line
                                            :chart {:bounds {:x "10%" :y "5%" :width "80%" :height "70%"}
                                                    :plot js/dimple.plot.line
                                                    :x-axis "key_as_string"
                                                    :y-axis "sum_up"}}})]
                         ]
                        ])))


(defn main []
  (om/root
    my-app
    app-state
    {:target (. js/document (getElementById "app"))}))
