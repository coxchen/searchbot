(ns searchbot.parsets
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :refer [put! <! >! chan timeout close!]]
            [searchbot.es :refer [es-agg filtered-agg]]))

(defn- build-parsets-agg
  [terms]
  (reduce (fn [agg-query a-term]
            {:aggs {(keyword a-term) (merge {:terms {:field a-term}} agg-query)}})
          {} terms))

(defn- walk-buckets [de-buck bucket prefix steps]
  (map #(de-buck % de-buck prefix (first steps) (rest steps))
           (get-in bucket [(first steps) :buckets])))

(defn- <-buckets [bucket de-buck prefix current-step steps]
  (if (= 0 (count steps))
    (merge prefix {current-step (:key bucket) :value (:doc_count bucket)})
    (let [prefix (merge prefix {current-step (:key bucket)})]
      (walk-buckets <-buckets bucket prefix steps))))

(defn- agg->parsets [agg-result all-steps]
  (vec (flatten (walk-buckets <-buckets agg-result {} all-steps))))

(defn- new-svg [graph-id w h]
  (-> js/d3 (.select graph-id) (.append "svg") (.attr "width" w) (.attr "height" h)))

(defcomponent parsets [cursor owner {:keys [agg width height] :or {width 1000 height 600} :as opts}]
  (init-state [_]
              {:continue? true
               :comm (chan)
               :parsets-agg (map keyword (:terms agg))
               :svg nil})
  (will-mount [_]
              (let [{:keys [parsets-agg comm]} (om/get-state owner)
                    {es-settings :es-settings} (om/get-shared owner)
                    agg-query (build-parsets-agg (reverse parsets-agg))
                    agg-query (filtered-agg {:body agg-query
                                             :filter (get-in (es-settings) [:default :filter])})]
                (.log js/console "# parsets agg query:" (pr-str agg-query))
                (go (while (:continue? (om/get-state owner))
                      (.log js/console "# running parsets aggregation")
                      (let [{agg-result :aggregations} (<! (es-agg "/es/stats_avc_1d/avc/_search" agg-query))]
                        (>! comm (agg->parsets agg-result parsets-agg)))
                      (<! (timeout 30000))))
                ))
  (render [_]
          (html
           [:.card
            [:.card-content
             [:span.card-title.black-text "# PARSETS"]
             [:div {:id "parsets"}]]]))
  (did-mount [_]
             (let [{:keys [comm parsets-agg]} (om/get-state owner)
                   svg (new-svg "#parsets" width height)
                   chart (-> js/d3
                              .parsets
                              (.dimensions (clj->js (map name parsets-agg)))
                              (.value (fn [d i] (. d -value))))]
               (om/update-state! owner #(assoc % :svg svg))
               (go (while (:continue? (om/get-state owner))
                     (let [parsets-data (<! comm)
                           _ (-> js/d3 (.select "#parsets > svg") .remove)
                           svg (new-svg "#parsets" width height)]
                       (.log js/console "# parsets aggregation:" (count parsets-data))
                       (-> svg (.datum (clj->js parsets-data))
                           (.call chart))
                       )))
               ))
  (will-unmount [_]
                (let [{:keys [comm]} (om/get-state owner)]
                  (.log js/console "### unmounting parsets")
                  (close! comm)
                  (om/update-state! owner #(assoc % :continue? false))
                  (om/update-state! owner #(assoc % :svg nil))
                  ))
  )
