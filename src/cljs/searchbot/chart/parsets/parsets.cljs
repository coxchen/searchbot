(ns searchbot.chart.parsets.parsets
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :refer [put! <! >! chan timeout close!]]
            [searchbot.es :refer [es-agg filtered-agg]]
            [searchbot.input.labels :as labels]
            [searchbot.charts :refer [es-chart]]
            [searchbot.chart.metricgraphics :as mg]
            [searchbot.chart.parsets.agg :refer [build-parsets-agg agg->parsets]]
            [searchbot.chart.parsets.chart :as chart]))

(defn- ->query
  [{:keys [agg-terms sub-aggs filters default-filter size] :or {size 5}}]
  (let [agg-query (build-parsets-agg {:terms (reverse agg-terms) :sub-aggs sub-aggs :size size})
        agg-query (filtered-agg (merge {:body agg-query}
                                 (merge {:filter default-filter}
                                        (:combined-query filters))))]
    agg-query))

(defn- reset-highlighted [owner]
  (-> js/d3 (.select (om/get-node owner "parsets-dims")) (.selectAll ".mg-bar") (.classed "highlight" false)))

(defn- do-parsets-agg
  [owner]
  (let [{:keys [continue? comm url get-agg-terms make-agg-query value-path]} (om/get-state owner)
        {es-settings :es-settings} (om/get-shared owner)]
    (when continue?
      (go (.log js/console "# running parsets aggregation:" (pr-str (get-agg-terms)))
          (let [{agg-result :aggregations} (<! (es-agg (or url (:url-agg (es-settings))) (make-agg-query)))]
            (let [parsets-data (agg->parsets agg-result (get-agg-terms) value-path)]
              (reset-highlighted owner)
              (om/update-state! owner #(assoc % :parsets-data parsets-data))
              (>! comm parsets-data))
            )))))

(defn- update-agg-terms-with-label
  [owner label-string]
  (let [agg-terms (labels/labelize label-string)
        agg-terms (map :name agg-terms)
        agg-terms (map keyword agg-terms)]
    (.log js/console "@ label-updated:" (pr-str agg-terms))
    (om/update-state! owner #(assoc % :parsets-agg agg-terms))
    (do-parsets-agg owner)
    ))

(defn highlight [data facets]
  (filter #(every? (fn [[k sel]] (or (empty? sel) (contains? sel (k %)))) facets) data))

(defn- select!
  [owner act selected]
  (let [{:keys [parsets-data highlighted]} (om/get-state owner)
        dim-key (first selected)
        sel-dim (into #{} (get highlighted dim-key))
        sel-facets (assoc highlighted dim-key (act sel-dim (last selected)))]
    (om/update-state! owner #(assoc % :highlighted sel-facets))
    (put! (:comm (om/get-state owner)) (highlight parsets-data sel-facets))))

(defn- build-bar [owner term-name]
  (let [chart-id (str (clojure.string/replace term-name #"\." "-") "_bar")
        chart-sel (str "#" chart-id)]
    (om/build mg/mg-bar
              (om/get-render-state owner :parsets-data)
              {:opts {:id chart-id :title term-name :height 150
                      :x "value" :y "key"
                      :trans-fn (fn [data-cursor]
                                  (let [reduced-data (->> data-cursor
                                                          (group-by (keyword term-name))
                                                          (mapv (fn [g] {:key (first g) :value (->> g last (map :value) (reduce +))}))
                                                          (sort-by :value >))]
                                    reduced-data))
                      :on-click-cb (fn [d i]
                                     (let [the-bar (-> js/d3 (.select chart-sel) (.selectAll ".mg-bar") js->clj (get-in [0 i]))
                                           was-highlighted? (-> js/d3 (.select the-bar) (.classed "highlight"))
                                           selected [(keyword term-name) (-> d js->clj (get "key"))]]
                                       (-> js/d3 (.select the-bar) (.classed "highlight" (not was-highlighted?)))
                                       (select! owner (if was-highlighted? disj conj) selected)))}})))

(defcomponent parsets [cursor owner {:keys [id agg value-path url height] :or {height 600 value-path ["doc_count"]} :as opts}]
  (init-state [_]
              (let [{:keys [es-settings ref-state]} (om/get-shared owner)]
                {:continue? true
                 :comm (chan)
                 :url url
                 :parsets-agg (->> agg :terms (map keyword))
                 :get-agg-terms #(:parsets-agg (om/get-state owner))
                 :size (or (get-in opts [:dimensions :size]) 5)
                 :make-agg-query #(->query {:agg-terms ((:get-agg-terms (om/get-state owner)))
                                            :sub-aggs (:sub agg)
                                            :size (:size (om/get-state owner))
                                            :filters (ref-state :filters)
                                            :default-filter (get-in (es-settings) [:default :filter])})
                 :value-path (apply vector (map keyword value-path))
                 :combined-query nil
                 :svg nil
                 :parsets-data []
                 :highlighted {}
                 :show-dimensions (get-in opts [:dimensions :show])}))
  (will-mount [_]
              (do-parsets-agg owner))
  (render-state [_ {:keys [parsets-agg get-agg-terms parsets-data show-dimensions]}]
                (html
                 [:.card {:ref "parsets-card"}
                  [:.card-content
                   [:.row
                    [:div.right {:style {:max-width "65%"}}
                     (let [label-string (->> (get-agg-terms) (map name) (clojure.string/join " "))]
                       (om/build labels/labels cursor
                                 {:opts {:labels (labels/labelize label-string)
                                         :on-label-updated! (partial update-agg-terms-with-label owner)}}))
                     ]
                    [:span.card-title.black-text [:p "# PARSETS"] [:p "[ " [:strong (->> parsets-agg (map name) (clojure.string/join " > "))] " ]"]]
                    [:p
                     [:input {:type "checkbox" :class "filled-in" :checked show-dimensions
                              :id "toggle-show-dimensions" :ref "toggle-show-dimensions"
                              :on-change (fn [_] (let [thisNode (om/get-node owner "toggle-show-dimensions")
                                                       checked (.-checked thisNode)]
                                                   (om/set-state! owner :show-dimensions checked)
                                                   (do-parsets-agg owner)))}]
                     [:label {:for "toggle-show-dimensions"} "Show Dimensional Charts"]]
                    ]
                   [:.row
                    [:.col {:class (if show-dimensions "s9" "s12") :id id :ref "parsets-div"}]
                    (if show-dimensions
                      [:.col {:class "s3" :ref "parsets-dims"}
                       (when-not (empty? parsets-data) (map #(build-bar owner (name %)) (get-agg-terms)))])
                    ]]]
                 ))
  (did-update [_ _ _]
              (if-let [filters ((:ref-state (om/get-shared owner)) :filters)]
                (let [local-combined-query (-> (om/get-state owner) :combined-query)
                      combined-query (:combined-query filters)]
                  (if-not (= local-combined-query combined-query)
                    (do
                      (om/update-state! owner #(assoc % :combined-query combined-query))
                      (do-parsets-agg owner)))
                  (om/observe owner filters))))
  (did-mount [_]
             (let [{:keys [comm parsets-agg]} (om/get-state owner)
                   parsets-div (om/get-node owner "parsets-div")
                   card-width #(* 0.95 (.-offsetWidth parsets-div))
                   get-svg #(chart/d3->svg {:id (str "#" id) :height height :width (card-width) :width-fn card-width})
                   parsets-chart #(chart/d3->parsets
                                   {:height height :width-fn card-width
                                    :dims (:parsets-agg (om/get-state owner))})]
               (.attach js/Waves (om/get-node owner "parsets-div") "waves-yellow")
               (go (while (:continue? (om/get-state owner))
                     (let [parsets-data (<! comm)
                           _ (-> js/d3 (.select (str "#" id " > svg")) .remove)
                           svg (get-svg)]
                       (when (< 0 (count parsets-data))
                         (.log js/console "# parsets aggregation:" (count parsets-data))
                         (-> svg (.datum (clj->js parsets-data))
                             (.call (parsets-chart)))
                         (.ripple js/Waves (om/get-node owner "parsets-div"))))))
               (if-let [filters ((:ref-state (om/get-shared owner)) :filters)]
                 (let [combined-query (:combined-query filters)]
                   (om/update-state! owner #(assoc % :combined-query combined-query))
                   (om/observe owner filters)))
               ))
  (will-unmount [_]
                (let [{:keys [comm]} (om/get-state owner)]
                  (.log js/console "### unmounting parsets")
                  (close! comm)
                  (om/update-state! owner #(assoc % :continue? false))
                  (om/update-state! owner #(assoc % :svg nil))
                  ))
  )
