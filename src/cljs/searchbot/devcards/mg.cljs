(ns searchbot.devcards.mg
  (:require-macros [devcards.core :as dc :refer [defcard deftest]])
  (:require [cljs.test :as t :include-macros true]
            [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [searchbot.chart.metricgraphics :as mg]
            [searchbot.charts :as charts]
            [searchbot.widgets :as widgets]
            ))

(defcard bar1
  (dc/om-root mg/mg-bar
              {:opts {:id "bar1" :height 150 :width 150 :x "val" :y "key"}})
  [{:val 10 :key "A"}
   {:val 200 :key "B"}]
  {:inspect-data true})



(defcard timeline
  (dc/om-root widgets/widgets)
  {:widgets
   [
    [
    {:type "es-chart" :cursor "agg"
     :id "time_line"
     :agg-key "TIMEAGG" :agg-top "traffic_over_time"
     :agg-view ["key" "key" "doc_count"]
     :draw-fn "draw-line"
     :chart {:bounds {:x "10%" :y "5%" :width "80%" :height "70%"}
             :plot "line"
             :x-axis  "key"
             :x-order "key"
             :y-axis  "value"
             :c-axis  "type"}}
     ]
    ]

   :agg {:div {:width "90%" :height 400}
         :TIMEAGG
         {:aggregations
          {:traffic_over_time
;;            {"buckets" [{ "doc_count" 1 "key" "2016-08-15T05:00:00.000Z"}]}
           {:buckets [{ :doc_count 1 :key "2016-08-15T05:00:00.000Z"}]}
           }}}
   }
  )
