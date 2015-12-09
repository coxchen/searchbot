(ns searchbot.devcards
  (:require-macros [devcards.core :as dc :refer [defcard deftest]])
  (:require [cljs.test :as t :include-macros true]
            [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [searchbot.chart.metricgraphics :as mg]
            ))

(defcard bar1
  (dc/om-root mg/mg-bar {:opts {:id "bar1" :height 300 :width 300 :x "x" :y "y"}})
  [{:x 10 :y 200}
   {:x 200 :y 300}]
  {:inspect-data true})
