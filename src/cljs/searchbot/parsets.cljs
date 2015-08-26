(ns searchbot.parsets
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :refer [put! <! >! chan timeout close!]]
            [searchbot.es :refer [es-agg filtered-agg]]
            [searchbot.input.labels :as labels]
            [searchbot.charts :refer [es-chart]]))

(defn- build-sub-aggs [sub-aggs]
  (if sub-aggs {:aggs sub-aggs} {}))

(defn- sort-sub [sub-aggs]
  (if-let [sort-field (-> sub-aggs keys first)]
    {:order {sort-field "desc"}}
    {}))

(defn- build-parsets-agg
  [terms sub-aggs]
  (reduce (fn [agg-query a-term]
            {:aggs (merge
                    {(keyword a-term)
                     (merge {:terms
                             (merge {:field a-term}
                                    (sort-sub sub-aggs))}
                            agg-query)}
                    sub-aggs)})
          (build-sub-aggs sub-aggs) terms))

(defn- build-terms-agg [prefix terms sub-aggs]
  (let [terms-aggs (for [term terms]
                     {(str prefix (name term)) (merge {:terms (merge {:field (name term)}
                                                                     (sort-sub sub-aggs))}
                                                      (build-sub-aggs sub-aggs))})
        terms-aggs (reduce merge terms-aggs)]
    terms-aggs))

(defn- walk-buckets [de-buck bucket prefix steps value-path]
  (map #(de-buck % de-buck prefix (first steps) (rest steps) value-path)
           (get-in bucket [(first steps) :buckets])))

(defn- <-buckets [bucket de-buck prefix current-step steps value-path]
  (if (= 0 (count steps))
    (merge prefix {current-step (:key bucket) :value (get-in bucket value-path)})
    (let [prefix (merge prefix {current-step (:key bucket)})]
      (walk-buckets de-buck bucket prefix steps value-path))))

(defn- agg->parsets [agg-result steps value-path]
  (vec (flatten (walk-buckets <-buckets agg-result {} steps value-path))))

(defn- ->query
  [{:keys [agg-terms sub-aggs agg-prefix-terms show-pies? filters default-filter]}]
  (let [agg-query (build-parsets-agg (reverse agg-terms) sub-aggs)
        agg-query (if (show-pies?)
                    {:aggs (merge (:aggs agg-query)
                                  (build-terms-agg agg-prefix-terms agg-terms sub-aggs))}
                    agg-query)
        agg-query (filtered-agg (merge {:body agg-query}
                                 (merge {:filter default-filter}
                                        (:combined-query filters))))]
    agg-query))

(defn- update-terms-aggs [owner terms-aggs]
  (om/update-state! owner :parsets-cursor #(assoc-in % [:terms-aggs :aggregations] terms-aggs)))

(defn- do-parsets-agg
  [owner]
  (let [{:keys [continue? comm url agg-prefix-terms get-agg-terms make-agg-query value-path]} (om/get-state owner)
        {es-settings :es-settings} (om/get-shared owner)]
    (when continue?
      (go (.log js/console "# running parsets aggregation:" (pr-str (get-agg-terms)))
          (let [{agg-result :aggregations} (<! (es-agg (or url (:url-agg (es-settings))) (make-agg-query)))]
            (let [term-keys (->> (get-agg-terms) (map #(->> % name (str agg-prefix-terms) keyword)))
                  terms-aggs (-> agg-result (select-keys term-keys))]
              (update-terms-aggs owner terms-aggs))
            (>! comm (agg->parsets agg-result (get-agg-terms) value-path))
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

(defn- new-svg [{:keys [id height width-fn]}]
  (-> js/d3 (.select id) (.append "svg") (.attr "width" (width-fn)) (.attr "height" height)))

(defn- make-parsets-chart [{:keys [height width-fn get-parsets-agg]}]
  (-> js/d3
      .parsets
      (.tension 0.8)
      (.width (width-fn))
      (.height height)
      (.dimensions (clj->js (map name (get-parsets-agg))))
      (.value (fn [d i] (. d -value)))))

(defcomponent parsets [cursor owner {:keys [agg value-path url height] :or {height 600 value-path ["doc_count"]} :as opts}]
  (init-state [_]
              (let [{:keys [es-settings ref-state]} (om/get-shared owner)
                    prefix-terms "terms_"]
                {:continue? true
                 :comm (chan)
                 :url url
                 :agg-prefix-terms prefix-terms
                 :parsets-agg (->> agg :terms (map keyword))
                 :get-agg-terms #(:parsets-agg (om/get-state owner))
                 :make-agg-query #(->query {:agg-prefix-terms prefix-terms
                                            :agg-terms ((:get-agg-terms (om/get-state owner)))
                                            :sub-aggs (:sub agg)
                                            :show-pies? (:show-pies? (om/get-state owner))
                                            :filters (ref-state :filters)
                                            :default-filter (get-in (es-settings) [:default :filter])})
                 :value-path (apply vector (map keyword value-path))
                 :combined-query nil
                 :svg nil
                 :parsets-cursor (om/root-cursor (atom {:div {:width "90%" :height 200}}))
                 :show-pies (:show-pies opts)
                 :show-pies? #(:show-pies (om/get-state owner))}))
  (will-mount [_]
              (do-parsets-agg owner))
  (render-state [_ {:keys [parsets-agg get-agg-terms agg-prefix-terms parsets-cursor show-pies ]}]
                (html
                 [:.card {:ref "parsets-card"}
                  [:.card-content
                   [:.row
                    [:span.card-title.black-text "# PARSETS [ " [:strong (->> parsets-agg (map name) (clojure.string/join " > "))] " ]"]
                    [:p
                     [:input {:type "checkbox" :class "filled-in" :checked show-pies
                              :id "toggle-show-pies" :ref "toggle-show-pies"
                              :on-change (fn [_] (let [thisNode (om/get-node owner "toggle-show-pies")
                                                       checked (.-checked thisNode)]
                                                   (om/set-state! owner :show-pies checked)
                                                   (do-parsets-agg owner)))}]
                     [:label {:for "toggle-show-pies"} "Show Pie Charts"]]
                    [:div.right {:style {:max-width "50%"}}
                     (let [label-string (->> (get-agg-terms) (map name) (clojure.string/join " "))]
                       (om/build labels/labels cursor
                                 {:opts {:labels (labels/labelize label-string)
                                         :on-label-updated! (partial update-agg-terms-with-label owner)}}))]
                    ]
                   [:.row
                    [:.col {:class (if show-pies "s9" "s12") :id "parsets" :ref "parsets-div"}]
                    [:.col {:class (if show-pies "s3" "hide")}
                     (for [term (get-agg-terms)]
                       (let [term-name (name term)]
                         (om/build es-chart
                                   parsets-cursor
                                   {:opts {:type "es-chart"
                                           :id (str term-name "_pie")
                                           :agg-key "terms-aggs"
                                           :agg-top (str agg-prefix-terms term-name)
                                           :agg-view ["key" "sum_usage"]
                                           :draw-fn "draw-ring"
                                           :chart {:bounds {:x "5%" :y "15%" :width "80%" :height "80%"}
                                                   :plot "pie"
                                                   :p-axis "sum_usage"
                                                   :c-axis "key"}}}
                                   )
                         ))
                     ]
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
                   card-width #(.-offsetWidth parsets-div)
                   get-svg #(new-svg {:id "#parsets" :height height :width (card-width) :width-fn card-width})
                   chart #(make-parsets-chart
                           {:height height :width (card-width) :width-fn card-width
                            :get-parsets-agg (fn [_] (:parsets-agg (om/get-state owner)))})]
               (.attach js/Waves (om/get-node owner "parsets-div") "waves-yellow")
               (go (while (:continue? (om/get-state owner))
                     (let [parsets-data (<! comm)
                           _ (-> js/d3 (.select "#parsets > svg") .remove)
                           svg (get-svg)]
                       (when (< 0 (count parsets-data))
                         (.log js/console "# parsets aggregation:" (count parsets-data))
                         (-> svg (.datum (clj->js parsets-data))
                             (.call (chart)))
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
