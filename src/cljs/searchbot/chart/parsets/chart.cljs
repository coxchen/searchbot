(ns searchbot.chart.parsets.chart)

(defn d3->svg [{:keys [id height width-fn]}]
  (-> js/d3 (.select id) (.append "svg") (.attr "width" (width-fn)) (.attr "height" height)))

(defn d3->parsets [{:keys [height width-fn dims]}]
  (-> js/d3
      .parsets
      (.tension 0.8)
      (.width (width-fn))
      (.height height)
      (.dimensions (clj->js (map name dims)))
      (.value (fn [d i] (. d -value)))))
