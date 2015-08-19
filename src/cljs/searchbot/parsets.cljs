(ns searchbot.parsets
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :refer [put! <! >! chan timeout close!]]
            [searchbot.es :refer [es-agg filtered-agg]]
            [searchbot.input.labels :as labels]))

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
                    {(keyword a-term) (merge
                                       {:terms (merge
                                                {:field a-term}
                                                (sort-sub sub-aggs))}
                                       agg-query)}
                    sub-aggs)})
          (build-sub-aggs sub-aggs) terms))

(defn- walk-buckets [de-buck bucket prefix steps value-path]
  (map #(de-buck % de-buck prefix (first steps) (rest steps) value-path)
           (get-in bucket [(first steps) :buckets])))

(defn- <-buckets [bucket de-buck prefix current-step steps value-path]
  (if (= 0 (count steps))
    (merge prefix {current-step (:key bucket) :value (get-in bucket value-path)})
    (let [prefix (merge prefix {current-step (:key bucket)})]
      (walk-buckets <-buckets bucket prefix steps value-path))))

(defn- agg->parsets [agg-result all-steps value-path]
  (vec (flatten (walk-buckets <-buckets agg-result {} all-steps value-path))))

(defn- new-svg [graph-id w h]
  (-> js/d3 (.select graph-id) (.append "svg") (.attr "width" w) (.attr "height" h)))

(defn- ->query
  [{:keys [agg-terms sub-aggs filters default-filter]}]
  (let [agg-query (build-parsets-agg (reverse agg-terms) sub-aggs)
        agg-query (filtered-agg (merge {:body agg-query}
                                 (merge {:filter default-filter}
                                        (:combined-query filters))))]
    agg-query))

(defn- do-parsets-agg
  [owner]
  (let [{:keys [continue? comm url get-agg-terms make-agg-query value-path]} (om/get-state owner)
        {es-settings :es-settings} (om/get-shared owner)]
    (when continue?
      (go (.log js/console "# running parsets aggregation:" (pr-str (get-agg-terms)))
          (let [{agg-result :aggregations} (<! (es-agg (or url (:url-agg (es-settings))) (make-agg-query)))]
            (>! comm (agg->parsets agg-result (get-agg-terms) value-path)))))))

(defn- update-agg-terms-with-label
  [owner label-string]
  (let [agg-terms (labels/labelize label-string)
        agg-terms (map :name agg-terms)
        agg-terms (map keyword agg-terms)]
    (.log js/console "@ label-updated:" (pr-str agg-terms))
    (om/update-state! owner #(assoc % :parsets-agg agg-terms))
    (do-parsets-agg owner)
    ))

(defn- make-parsets-chart [{:keys [height width get-parsets-agg]}]
  (-> js/d3
      .parsets
      (.tension 0.8)
      (.width width)
      (.height height)
      (.dimensions (clj->js (map name (get-parsets-agg))))
      (.value (fn [d i] (. d -value)))))

(defcomponent parsets [cursor owner {:keys [agg value-path url height] :or {height 600 value-path ["doc_count"]} :as opts}]
  (init-state [_]
              (let [{:keys [es-settings ref-state]} (om/get-shared owner)]
                {:continue? true
                 :comm (chan)
                 :url url
                 :parsets-agg (map keyword (:terms agg))
                 :get-agg-terms #(:parsets-agg (om/get-state owner))
                 :make-agg-query #(->query {:agg-terms ((:get-agg-terms (om/get-state owner)))
                                            :sub-aggs (:sub agg)
                                            :filters (ref-state :filters)
                                            :default-filter (get-in (es-settings) [:default :filter])})
                 :value-path (apply vector (map keyword value-path))
                 :combined-query nil
                 :svg nil}))
  (will-mount [_]
              (do-parsets-agg owner))
  (render [_]
          (html
           [:.card {:ref "parsets-card"}
            [:.card-content
             [:div.right {:style {:max-width "50%"}}
              (let [get-agg-terms #(:parsets-agg (om/get-state owner))
                    label-string (->> (get-agg-terms)
                                      (map name)
                                      (clojure.string/join " "))]
                (om/build labels/labels cursor
                          {:opts {:labels (labels/labelize label-string)
                                  :on-label-updated! (partial update-agg-terms-with-label owner)}}))]
             [:span.card-title.black-text
              "# PARSETS [ "
              [:strong (->> (:parsets-agg (om/get-state owner))
                            (map name)
                            (clojure.string/join " > "))]
              " ]"]
             [:div#parsets {:ref "parsets-div"}]]]))
  (did-update [_ _ _]
              (let [filters ((:ref-state (om/get-shared owner)) :filters)
                    local-combined-query (-> (om/get-state owner) :combined-query)
                    combined-query (:combined-query filters)]
                (if-not (= local-combined-query combined-query)
                  (do
                    (om/update-state! owner #(assoc % :combined-query combined-query))
                    (do-parsets-agg owner)))
                (om/observe owner filters)))
  (did-mount [_]
             (let [{:keys [comm parsets-agg]} (om/get-state owner)
                   parsets-div (om/get-node owner "parsets-div")
                   card-width (.-offsetWidth parsets-div)
                   get-svg #(new-svg "#parsets" card-width height)
                   chart #(make-parsets-chart
                           {:height height :width card-width
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
               (let [filters ((:ref-state (om/get-shared owner)) :filters)
                     combined-query (:combined-query filters)]
                 (om/update-state! owner #(assoc % :combined-query combined-query))
                 (om/observe owner filters))
               ))
  (will-unmount [_]
                (let [{:keys [comm]} (om/get-state owner)]
                  (.log js/console "### unmounting parsets")
                  (close! comm)
                  (om/update-state! owner #(assoc % :continue? false))
                  (om/update-state! owner #(assoc % :svg nil))
                  ))
  )
