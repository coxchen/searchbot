(ns searchbot.charts
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]))

;;;;;;;;;;
;; charts

(defn- get-div-dimensions
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

(defn- ->plot
  [dimple-chart]
  (case dimple-chart
    "pie" js/dimple.plot.pie
    "bar" js/dimple.plot.bar
    "line" js/dimple.plot.line
    nil))

(defn- draw-bar [data div {:keys [id chart]}]
  (let [{:keys [bounds plot
                x-axis y-axis]}   chart
        dimple-chart              (->dimple div id bounds)
        x                         (.addMeasureAxis dimple-chart "x" x-axis)
        y                         (.addCategoryAxis dimple-chart "y" y-axis)
        s                         (.addSeries dimple-chart y-axis (->plot plot) (clj->js [y x]))]
    (.addOrderRule y x-axis)
    (aset s "data" (clj->js data))
    (.addLegend dimple-chart "83%" "10%" "20%" "50%" "right")
    (.draw dimple-chart)))


(defn- draw-ring [data div {:keys [id chart]}]
  (let [{:keys [bounds plot
                p-axis c-axis]}   chart
        dimple-chart              (->dimple div id bounds)
        p                         (.addMeasureAxis dimple-chart "p" p-axis)
        s                         (.addSeries dimple-chart c-axis (->plot plot) (clj->js [p]))]
    (aset s "innerRadius" "50%")
    (aset s "data" (clj->js data))
    (.addLegend dimple-chart "80%" "10%" "10%" "90%" "left")
    (.draw dimple-chart)))

(defn- draw-line [data div {:keys [id chart]}]
  (let [{:keys [bounds plot
                x-axis y-axis
                c-axis x-order]}  chart
        dimple-chart              (->dimple div id bounds)
        x                         (.addCategoryAxis dimple-chart "x" x-axis)
        y                         (.addMeasureAxis dimple-chart "y" y-axis)
        s                         (.addSeries dimple-chart c-axis (->plot plot) (clj->js [x y]))]
    (.addOrderRule x x-order)
    (aset s "interpolation" "cardinal")
    (aset s "data" (clj->js data))
    (.addLegend dimple-chart "95%" "0%" "5%" "20%" "right")
    (.draw dimple-chart)))

(defn- trans-line [agg-data agg-view]
  (let [mapped (map (fn [data]
                      (for [v (drop 2 agg-view)]
                        (merge (select-keys data (vec (take 2 agg-view)))
                               {:value (get data v) :type v})))
                    agg-data)
        flattened (flatten mapped)]
    flattened))

(defn- chart-fn [fn-name]
  (case fn-name
    "draw-bar"  draw-bar
    "draw-ring" draw-ring
    "draw-line" draw-line
    nil))

(defn- trans-fn [fn-name]
  (case fn-name
    "draw-line" trans-line
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
  [cursor {:keys [id chart agg-key agg-top agg-view draw-fn] :as opts}]
  (when-let [data (get-agg-buckets cursor agg-key agg-top)]
    (let [agg-view (map keyword agg-view)
          flattened (flatten-agg-buckets agg-view data)
          transed (do-trans (trans-fn draw-fn) flattened agg-view)]
      ((chart-fn draw-fn) transed (:div cursor) opts))))

(defcomponent detail [app owner opts]
  (render [_]
          (html
           [:pre {:style {:font-size "8pt"}}
            [:code.json (.stringify js/JSON (clj->js opts) nil 4)]])))

(defcomponent es-chart [cursor owner {:keys [id] :as opts}]
  (will-mount [_]
              ;; Add event listener that will update width & height when window is resized
              (.addEventListener js/window
                                 "resize" (fn []
                                            (let [{:keys [width height]} (get-div-dimensions id)]
                                              (om/update! cursor :div {:width width :height height})))))
  (render [_]
          (let [{:keys [width height]} (:div cursor)]
            (html [:.card
                   [:.card-content
                    [:span.card-title.black-text
                     (:agg-key opts)
                     [:a.btn-floating.btn-flat.white.waves-effect.waves-red.activator.right
                      [:i.mdi-action-settings.grey-text]]]
                    [:div {:id id :width width :height height}]
                    ]
                   [:.card-reveal
                    [:span.card-title.grey-text.text-darken-4
                     (:agg-key opts)
                     [:i.mdi-navigation-close.right]
                     [:ul.collapsible.popout {:data-collapsible "accordion"}
                      [:li
                       [:div.collapsible-header [:i.fa.fa-trello] "Spec"]
                       [:div.collapsible-body
                        (om/build detail cursor {:opts opts})
                        ]]
                      [:li
                       [:div.collapsible-header [:i.fa.fa-list-ol] "Data"]
                       [:div.collapsible-body
                        (let [agg-key (-> opts :agg-key keyword)
                              agg-top (-> opts :agg-top keyword)
                              chart-data (-> cursor (get agg-key) :aggregations agg-top)]
;;                           (.log js/console (pr-str "----------"))
;;                           (.log js/console (pr-str (keys cursor)))
                          (om/build detail cursor {:opts chart-data}))
                        ]]
                      ]
                     ]]
                   ])))
  (did-mount [_]
             (do-chart cursor opts))
  (did-update [_ _ _]
              (let [n (.getElementById js/document id)]
                (while (.hasChildNodes n)
                  (.removeChild n (.-lastChild n))))
              (do-chart cursor opts)))
