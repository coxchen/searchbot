(ns searchbot.core
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [om.core :as om :include-macros true]
;;             [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [cljs-http.client :as http]
            [goog.events :as events]
            [cljs.core.async :refer [put! <! >! chan timeout]]
            [searchbot.widgets :refer [header agg-summary agg-table chart]]))

(defonce app-state (atom {:header-text "AVC realtime aggregation"
                          :avc-count 0
                          :agg {}
                          :chart {:div {:width "90%" :height 300}
                                  :data [{:value 240000 :timestamp "2014-01-01"}
                                         {:value 260000 :timestamp "2014-02-01"}
                                         {:value 290000 :timestamp "2014-03-01"}
                                         {:value 70000  :timestamp "2014-04-01"}
                                         {:value 100000 :timestamp "2014-05-01"}
                                         {:value 120000 :timestamp "2014-06-01"}
                                         {:value 240000 :timestamp "2014-07-01"}
                                         {:value 220000 :timestamp "2014-08-01"}
                                         {:value 360000 :timestamp "2014-09-01"}
                                         {:value 260000 :timestamp "2014-10-01"}
                                         {:value 250000 :timestamp "2014-11-01"}
                                         {:value 190000 :timestamp "2014-12-01"}]}
                          }))

(defcomponent my-app [app owner]
  (render [this] (html [:div
                        (om/build header app)
                        [:.row [:.col-lg-4 (om/build agg-summary app)]]
                        [:.row
                         [:.col-lg-3
                          (om/build agg-table app
                                    {:opts {:agg-key "SSID AGG"
                                            :agg-top "ssids"
                                            :header [{:label "SSID" :agg :key}
                                                     {:label "Client Count" :agg :client_count}
                                                     {:label "Usage" :agg :sum_usage}]
                                            :body {:query {:filtered {:query {:match_all {}}}}
                                                   :aggs {:ssids
                                                          {:terms {:field "ssid" :order {:sum_usage "desc"}}
                                                           :aggs {:sum_usage {:sum {:field "usage"}}
                                                                  :client_count
                                                                  {:cardinality {:field "client_mac.hash"
                                                                                 :precision_threshold 50000}}}}}}}})]
                         [:.col-lg-4
                          (om/build agg-table app
                                    {:opts {:agg-key "APP AGG"
                                            :agg-top "apps"
                                            :header [{:label "Application" :agg :key}
                                                     {:label "Usage" :agg :sum_usage}
                                                     {:label "UpLink" :agg :sum_up}
                                                     {:label "DownLink" :agg :sum_down}]
                                            :body {:aggs {:apps
                                                          {:terms {:field "app" :order {:sum_usage "desc"}}
                                                           :aggs {:sum_usage {:sum {:field "usage"}}
                                                                  :sum_up {:sum {:field "up"}}
                                                                  :sum_down {:sum {:field "down"}}}}}}}})]

                         ]
                        [:.row
                         [:.col-lg-3
                          (om/build chart (:chart app)
                                    {:opts {:id "bar"
                                            :draw-fn :draw-bar
                                            :chart {:bounds {:x "5%" :y "15%" :width "80%" :height "80%"}
                                                    :plot js/dimple.plot.bar
                                                    :x-axis "timestamp"
                                                    :y-axis "value"}}})
                          ]
                         [:.col-lg-3
                          (om/build chart (:chart app)
                                    {:opts {:id "pie"
                                            :chart {:bounds {:x "5%" :y "15%" :width "80%" :height "80%"}
                                                    :plot js/dimple.plot.pie
                                                    :p-axis "value"
                                                    :c-axis "timestamp"}
                                            :draw-fn :draw-ring}})]
                         [:.col-lg-3
                          (om/build chart (:chart app)
                                    {:opts {:id "line"
                                            :draw-fn :draw-bar
                                            :chart {:bounds {:x "10%" :y "5%" :width "80%" :height "70%"}
                                                    :plot js/dimple.plot.line
                                                    :x-axis "timestamp"
                                                    :y-axis "value"}}})
                          ]
                         ]
                        ])))


(defn main []
  (om/root
    my-app
    app-state
    {:target (. js/document (getElementById "app"))}))
