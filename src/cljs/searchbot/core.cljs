(ns searchbot.core
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [om.core :as om :include-macros true]
;;             [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [cljs-http.client :as http]
            [goog.events :as events]
            [cljs.core.async :refer [put! <! >! chan timeout]]
            [searchbot.widgets :refer [header agg-summary agg-table]]))

(defonce app-state (atom {:header-text "AVC realtime aggregation"
                          :avc-count 0
                          :agg {}}))

(defcomponent my-app [app owner]
  (render [this] (html [:div
                        (om/build header app)
                        [:.row [:.col-lg-4 (om/build agg-summary app)]]
                        [:.row
                         [:.col-lg-4
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
                        ])))



(defn main []
  (om/root
    my-app
    app-state
    {:target (. js/document (getElementById "app"))}))
