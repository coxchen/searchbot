(ns searchbot.chart.metricgraphics
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]))

(defn bar-chart [cursor owner {:keys [id title height x y trans-fn] :as opts}]
  (let [card (om/get-node owner "mg-bar-card")
        card-width #(.-offsetWidth card)
        transed (if trans-fn (trans-fn cursor) cursor)]
    (if transed
      (->> {:title title
            :data (if trans-fn (trans-fn cursor) cursor)
            :chart_type "bar"
            :x_accessor x
            :y_accessor y
            :xax_count 3
            :width (- (card-width) 20)
            :height height
            :right 10
            :animate_on_load true
            :target (str "#" id)}
           clj->js
           (.data_graphic js/MG)))))

(defcomponent mg-bar [cursor owner {:keys [id title height x y trans-fn] :as opts}]
  (render [_] (html [:.card [:.card-content [:div {:id id :ref "mg-bar-card"}]]]))
  (did-update [_ _ _]
              (bar-chart cursor owner opts))
  (did-mount [_]
             (bar-chart cursor owner opts)))
