(ns searchbot.parsets
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :refer [put! <! >! chan timeout close!]]
            [searchbot.es :refer [es-agg filtered-agg]]
            [searchbot.input.labels :as labels]))

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

(defn- ->query
  [{:keys [agg-terms default-filter]}]
  (let [agg-query (build-parsets-agg (reverse agg-terms))
        agg-query (filtered-agg {:body agg-query
                                 :filter default-filter})]
    agg-query))

(defn- do-parsets-agg
  [owner]
  (let [{:keys [continue? comm url get-agg-terms make-agg-query]} (om/get-state owner)
        {es-settings :es-settings} (om/get-shared owner)]
    (when continue?
      (go (.log js/console "# running parsets aggregation:" (pr-str (get-agg-terms)))
          (let [{agg-result :aggregations} (<! (es-agg (or url (:url-agg (es-settings))) (make-agg-query)))]
            (>! comm (agg->parsets agg-result (get-agg-terms))))))))

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
      (.width width)
      (.height height)
      (.dimensions (clj->js (map name (get-parsets-agg))))
      (.value (fn [d i] (. d -value)))))

(defcomponent parsets [cursor owner {:keys [agg url height] :or {height 600} :as opts}]
  (init-state [_]
              {:continue? true
               :comm (chan)
               :url url
               :parsets-agg (map keyword (:terms agg))
               :get-agg-terms #(:parsets-agg (om/get-state owner))
               :make-agg-query #(->query {:agg-terms ((:get-agg-terms (om/get-state owner)))
                                          :default-filter (get-in (:es-settings (om/get-shared owner)) [:default :filter])})
               :svg nil})
  (will-mount [_]
              (do-parsets-agg owner))
  (render [_]
          (html
           [:.card
            [:.card-content
             [:div.right {:style {:max-width "50%"}}
              (let [get-agg-terms #(:parsets-agg (om/get-state owner))
                    label-string (->> (get-agg-terms)
                                      (map name)
                                      (clojure.string/join " "))]
                (om/build labels/labels cursor {:opts
                                                {:labels (labels/labelize label-string)
                                                 :on-label-updated! (partial update-agg-terms-with-label owner)}}))]
             [:span.card-title.black-text
              "# PARSETS [ "
              [:strong (->> (:parsets-agg (om/get-state owner))
                            (map name)
                            (clojure.string/join " > "))]
              " ]"]
             [:div#parsets {:ref "parsets-div"}]]]))
  (did-mount [_]
             (let [{:keys [comm parsets-agg]} (om/get-state owner)
                   card-width (->> "parsets-div" (om/get-node owner) (.-offsetWidth))
                   get-svg #(new-svg "#parsets" card-width height)
                   svg (get-svg)
                   chart #(make-parsets-chart
                           {:height height :width card-width
                            :get-parsets-agg (fn [_] (:parsets-agg (om/get-state owner)))})
                   ]
               (om/update-state! owner #(assoc % :svg svg))
               (go (while (:continue? (om/get-state owner))
                     (let [parsets-data (<! comm)
                           _ (-> js/d3 (.select "#parsets > svg") .remove)
                           svg (get-svg)]
                       (.log js/console "# parsets aggregation:" (count parsets-data))
                       (-> svg (.datum (clj->js parsets-data))
                           (.call (chart))
                           )
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
