(ns searchbot.core
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [cljs-http.client :as http]
            [goog.events :as events]
            [cljs.core.async :refer [put! <! >! chan timeout]]))

(defonce app-state (atom {:header-text "AVC realtime aggregation"
                          :avc-count 0
                          :agg {}}))

(defn avc-count [url]
  (let [c (chan)]
    (go (let [{{avcCount :count} :body} (<! (http/get url))]
          (>! c avcCount)))
    c))

(defn avc-agg [url post-body]
  (let [c (chan)]
    (go (let [{agg :body} (<! (http/post url {:json-params post-body}))]
          (>! c agg)))
    c))

(defcomponent header [app owner opts]
  (will-mount [_]
;;               (om/transact! app :avc-count (fn [_] 0))
              (go (while true
                    (let [avcCount (<! (avc-count (:url opts)))]
                      (.log js/console (pr-str avcCount))
                      (om/update! app [:avc-count] avcCount))
                    (<! (timeout (:poll-interval opts))))))
  (render [_]
          (html [:h2 (:header-text app) " with " (:avc-count app) " raw data in ES"])))

(defcomponent agg-summary [app owner opts]
  (render [this] (html [:table.table.table-hover.table-condensed
                        [:thead
                         [:tr
                          [:th {:align "right"} "Aggregation"]
                          [:th {:align "right"} "Records aggregated"]
                          [:th {:align "right"} "Time (ms)"]]]
                        [:tbody
                         (for [aggKey (-> app :agg keys)]
                           [:tr
                            [:td [:span (name aggKey)]]
                            [:td (-> app :agg aggKey :hits :total)]
                            [:td (-> app :agg aggKey :took)]])]])))

(defcomponent agg-table [app owner opts]
  (will-mount [_]
              (go (while true
                    (let [agg-key (-> opts :agg-key keyword)
                          agg-resp (<! (avc-agg (:url opts) (:body opts)))]
;;                       (.log js/console (pr-str aggResp))
                      (om/update! app [:agg agg-key] agg-resp)
;;                       (.log js/console (pr-str "agg" (@app :agg)))
;;                       (.log js/console (pr-str "agg keys" (-> @app :agg agg-key :aggregations keys)))
                      )
                    (<! (timeout (:poll-interval opts))))))
  (render [this] (html [:.panel.panel-info
                        [:.panel-heading (-> opts :agg-key name)]
                        [:.panel-body
                         [:table.table.table-hover.table-condensed
                          [:thead
                           [:tr
                            (for [col (:header opts)]
                              [:th (:label col)])]]
                          [:tbody
                           (let [agg-key (-> opts :agg-key keyword)
                                 agg-top (-> opts :agg-top keyword)]
                             (for [x (-> app :agg (get agg-key) :aggregations agg-top :buckets)]
                               [:tr
                                [:td (->> opts :header first :field (get x))]
                                [:td (get-in x [:client_count :value])]
                                [:td (get-in x [:sum_usage :value])]]))]]]])))

(defcomponent my-app [app owner]
  (render [this] (html [:div
                        (om/build header app
                                  {:opts {:url "/es/avc_1d/avc/_count"
                                          :poll-interval 20000}})
                        [:.row
                         [:.col-lg-4
                          (om/build agg-summary app
                                    {:opts {:url "/es/avc_1d/avc/_search"
                                            :poll-interval 20000
                                            :body {:query {:filtered {:query {:match_all {}}}}
                                                   :aggs {:ssids
                                                          {:terms {:field "ssid" :size 10 :order {:sum_usage "desc"}}
                                                           :aggs {:sum_usage {:sum {:field "usage"}}
                                                                  :client_count
                                                                  {:cardinality {:field "client_mac.hash"
                                                                                 :precision_threshold 1000}}}}}}}
                                     })]]
                        [:.row
                         [:.col-lg-4
                          (om/build agg-table app
                                    {:opts {:agg-key "SSID AGG"
                                            :agg-top "ssids"
                                            :header [{:label "SSID" :field :key}
                                                     {:label "Client Count" :field :client_count}
                                                     {:label "Usage" :field :sum_usage}]
                                            :url "/es/avc_1d/avc/_search"
                                            :body {:query {:filtered {:query {:match_all {}}}}
                                                   :aggs {:ssids
                                                          {:terms {:field "ssid" :size 10 :order {:sum_usage "desc"}}
                                                           :aggs {:sum_usage {:sum {:field "usage"}}
                                                                  :client_count
                                                                  {:cardinality {:field "client_mac.hash"
                                                                                 :precision_threshold 1000}}}}}}}}
                                    )]]
                        ])))



(defn main []
  (om/root
    my-app
    app-state
    {:target (. js/document (getElementById "app"))}))
