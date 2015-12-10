(ns searchbot.devcards.mg
  (:require-macros [devcards.core :as dc :refer [defcard deftest]])
  (:require [cljs.test :as t :include-macros true]
            [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [searchbot.chart.metricgraphics :as mg]
            ))

(defcard bar1
  (dc/om-root mg/mg-bar
              {:opts {:id "bar1" :height 150 :width 150 :x "val" :y "key"}})
  [{:val 10 :key "A"}
   {:val 200 :key "B"}]
  {:inspect-data true})
