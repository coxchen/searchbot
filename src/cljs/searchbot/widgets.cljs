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

(def poll-interval 20000)

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
;;               (om/transact! app :avc-count (fn [_] 0))
              (go (while true
                    (let [avcCount (<! (avc-count))]
                      (.log js/console (pr-str "[avc count]" avcCount))
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
                         (for [aggKey (-> app :agg keys)]
                           [:tr
                            [:td [:span (name aggKey)]]
                            [:td (-> app :agg aggKey :hits :total str commify)]
                            [:td (-> app :agg aggKey :took str commify)]])]])))

(defcomponent agg-table [app owner opts]
  (will-mount [_]
              (go (while true
                    (let [agg-key (-> opts :agg-key keyword)
                          agg-resp (<! (avc-agg (:body opts)))]
;;                       (.log js/console (pr-str agg-key))
;;                       (.log js/console (pr-str agg-resp))
                      (om/update! app [:agg agg-key] agg-resp)
;;                       (.log js/console (pr-str "agg" (@app :agg)))
;;                       (.log js/console (pr-str "agg keys" (-> @app :agg agg-key :aggregations keys)))
                      )
                    (<! (timeout poll-interval)))))
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
