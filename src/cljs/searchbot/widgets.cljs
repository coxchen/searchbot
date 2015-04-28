(ns searchbot.widgets
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [om.core :as om :include-macros true]
;;             [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [put! <! >! chan timeout]]))


(defn es-url [purpose]
  (case purpose
    :count "/es/avc_1d/avc/_count"
    :agg "/es/avc_1d/avc/_search"
    "/es"))

(def poll-interval 60000)

(defn commify [s]
  (let [s (reverse s)]
    (loop [[group remainder] (split-at 3 s)
           result '()]
      (if (empty? remainder)
        (apply str (reverse (rest (concat result (list \,) group)))) ; rest to remove extraneous ,
        (recur (split-at 3 remainder) (concat result (list \,) group))))))


(defn avc-count []
  (let [c (chan)]
    (go (let [{{avcCount :count} :body} (<! (http/get (es-url :count)))]
          (>! c avcCount)))
    c))

(defn avc-agg [post-body]
  (let [c (chan)]
    (go (let [{agg :body} (<! (http/post (es-url :agg) {:json-params post-body}))]
          (>! c agg)))
    c))

(defcomponent header [app owner opts]
  (will-mount [_]
              (go (while true
                    (let [avcCount (<! (avc-count))]
                      (om/update! app [:avc-count] avcCount))
                    (<! (timeout poll-interval)))))
  (render [_]
          (html [:h2 (:header-text app) " with " (-> app :avc-count str commify) " raw data in ES"])))

(defcomponent agg-summary [app owner opts]
  (render [this] (html [:table.table.table-hover.table-condensed
                        [:thead
                         [:tr
                          [:th {:align "right"} "Aggregation"]
                          [:th {:align "right"} "Records aggregated"]
                          [:th {:align "right"} "Time (ms)"]]]
                        [:tbody
                         (for [aggKey (filter #(not= :div %) (-> app :agg keys))]
                           [:tr
                            [:td [:span (name aggKey)]]
                            [:td (-> app :agg aggKey :hits :total str commify)]
                            [:td (-> app :agg aggKey :took str commify)]])]])))

(defcomponent agg-table [app owner opts]
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
                                [:td (->> opts :header first :agg (get x))]
                                (for [d (->> opts :header rest)]
                                  [:td (-> x (get-in [(:agg d) :value]) str commify)])]))]]]])))

(defcomponent aggregator [app owner opts]
  (will-mount [_]
              (go (while true
                    (let [agg-key (-> opts :agg-key keyword)
                          agg-resp (<! (avc-agg (merge (:body opts)
                                                       {:query
                                                        {:filtered
                                                         {:query {:match_all {}}
                                                          :filter {:range {:timestamp {:gte "now-12h" :lte "now"}}}}}})))]
;;                       (.log js/console (pr-str agg-key))
;;                       (.log js/console (pr-str agg-resp))
                      (om/update! app [:agg agg-key] agg-resp))
                    (<! (timeout poll-interval)))))
  (render [_] (html [:.hide-agg "Aggregator:" (-> opts :agg-key)])))

;;;;;;;;;;
;; charts

(defn get-div-dimensions
  "Get width and height of a div with a specified id."
  [id]
  (let [e (.getElementById js/document id)
        x (.-clientWidth e)
        y (.-clientHeight e)]
    {:width x :height y}))

(defn- ->dimple [div id bounds]
  (let [{:keys [width height]}    div
        Chart                     (.-chart js/dimple)
        svg                       (.newSvg js/dimple (str "#" id) width height)
        dimple-chart              (.setBounds (Chart. svg) (:x bounds) (:y bounds) (:width bounds) (:height bounds))]
    dimple-chart))


(defn- draw-bar [data div {:keys [id chart]}]
  (let [{:keys [bounds plot
                x-axis y-axis]}   chart
        dimple-chart              (->dimple div id bounds)
        x                         (.addCategoryAxis dimple-chart "x" x-axis)
        y                         (.addMeasureAxis dimple-chart "y" y-axis)
        s                         (.addSeries dimple-chart nil plot (clj->js [x y]))]
    (aset s "data" (clj->js data))
    (.addLegend dimple-chart "5%" "10%" "20%" "10%" "right")
    (.draw dimple-chart)))


(defn- draw-ring [data div {:keys [id chart]}]
  (let [{:keys [bounds plot
                p-axis c-axis]}   chart
        dimple-chart              (->dimple div id bounds)
        p                         (.addMeasureAxis dimple-chart "p" p-axis)
        s                         (.addSeries dimple-chart c-axis plot (clj->js [p]))]
    (aset s "innerRadius" "50%")
    (aset s "data" (clj->js data))
    (.addLegend dimple-chart "80%" "10%" "10%" "90%" "left")
    (.draw dimple-chart)))

(defn- draw-line [data div {:keys [id chart]}]
  (let [{:keys [bounds plot
                x-axis y-axis
                c-axis       ]}   chart
        dimple-chart              (->dimple div id bounds)
        x                         (.addCategoryAxis dimple-chart "x" x-axis)
        y                         (.addMeasureAxis dimple-chart "y" y-axis)
        s                         (.addSeries dimple-chart c-axis plot (clj->js [x y]))]
    (.addOrderRule x "Date")
    (aset s "interpolation" "cardinal")
    (aset s "data" (clj->js data))
    (.addLegend dimple-chart "95%" "0%" "5%" "20%" "right")
    (.draw dimple-chart)))

(defn chart-fn [fn-name]
  (case fn-name
    :draw-bar  draw-bar
    :draw-ring draw-ring
    :draw-line draw-line
    nil))

(defn trans-line [agg-data agg-view]
  (let [mapped (map (fn [data]
                      (for [v (rest agg-view)]
                        (merge (select-keys data [(first agg-view)])
                               {:value (get data v) :type v})))
                    agg-data)
        flattened (flatten mapped)]
    flattened))

(defn trans-fn [fn-name]
  (case fn-name
    :trans-line trans-line
    nil))

(defn- do-trans [trans agg-data agg-view]
  (if trans (trans agg-data agg-view) agg-data))

(defn- agg-val
  [agg-data views]
  (reduce merge
          (map (fn [view]
                 (if (map? (get agg-data view))
                   {view (get-in agg-data [view :value])}
                   (select-keys agg-data [view])))
               views)))

(defn- flatten-agg-buckets
  [agg-view agg-buckets]
  (map (fn [bucket]
         (agg-val bucket agg-view))
       agg-buckets))

(defn- get-agg-buckets
  [cursor agg-key agg-top]
  (-> cursor (get-in [(keyword agg-key) :aggregations (keyword agg-top) :buckets]) seq))

(defn- do-chart
  [cursor {:keys [id chart agg-key agg-top agg-view trans draw-fn] :as opts}]
  (when-let [data (get-agg-buckets cursor agg-key agg-top)]
    (let [flattened (flatten-agg-buckets agg-view data)
          transed (do-trans (trans-fn trans) flattened agg-view)]
      ((chart-fn draw-fn) transed (:div cursor) opts))))


(defcomponent es-chart [cursor owner {:keys [id] :as opts}]
  (will-mount [_]
              ;; Add event listener that will update width & height when window is resized
              (.addEventListener js/window
                                 "resize" (fn []
                                            (let [{:keys [width height]} (get-div-dimensions id)]
                                              (om/update! cursor :div {:width width :height height})))))
  (render [_]
          (let [{:keys [width height]} (:div cursor)]
            (html [:div {:id id :width width :height height}])))
  (did-mount [_]
             (do-chart cursor opts))
  (did-update [_ _ _]
              (let [n (.getElementById js/document id)]
                (while (.hasChildNodes n)
                  (.removeChild n (.-lastChild n))))
              (do-chart cursor opts)))
