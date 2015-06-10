(ns searchbot.widgets
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [put! <! >! chan timeout]]
            [searchbot.charts :refer [es-chart]]
            ))

(def poll-interval 30000)

(defn commify [s]
  (let [s (reverse s)]
    (loop [[group remainder] (split-at 3 s)
           result '()]
      (if (empty? remainder)
        (apply str (reverse (rest (concat result (list \,) group)))) ; rest to remove extraneous ,
        (recur (split-at 3 remainder) (concat result (list \,) group))))))


(defn es-count [api-url]
  (let [c (chan)]
    (go (let [{{docCount :count} :body} (<! (http/get api-url))]
          (>! c docCount)))
    c))

(defn es-agg [api-url post-body]
  (let [c (chan)]
    (go (let [{agg :body} (<! (http/post api-url
                                         {:json-params
                                          (-> post-body (assoc :search_type "count"))}))]
          (>! c agg)))
    c))

(defcomponent header [app owner opts]
  (will-mount [_]
              (go (while true
                    (let [docCount (<! (es-count (-> app :es-api :count)))]
                      (om/update! app [:es-count] docCount))
                    (<! (timeout (or (-> app :poll-interval) poll-interval))))))
  (render [_]
          (html [:h3 (:header-text app) " with " (-> app :es-count str commify) " raw data in ES"])))

(defcomponent agg-summary [app owner opts]
  (render [this] (html [:.col.s4
                        [:.card.blue-grey.darken-1
                         [:.card-content.amber-text.text-accent-4
                          [:table.responsive-table.hoverable.bordered ;.table.table-hover.table-condensed
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
                               [:td (-> app :agg aggKey :took str commify)]])]]]]])))

(defcomponent detail [app owner opts]
  (render [_]
          (html
           [:pre {:style {:font-size "8pt"}}
            [:code.json (.stringify js/JSON (clj->js opts) nil 4)]])))

(defcomponent agg-table [app owner opts]
  (render [this] (html [:.card.blue-grey.darken-1
                        [:.card-content.activator.waves-effect.waves-block.waves-light.amber-text.text-accent-4
                         [:span.card-title (-> opts :agg-key name)
                          [:a.btn-floating.btn-flat.waves-effect.waves-light.activator.right
                           [:i.mdi-action-settings.right]]]
                         [:table.responsive-table.hoverable.bordered ;.table.table-hover.table-condensed
                          [:thead
                           [:tr
                            (for [col (:header opts)]
                              [:th (:label col)])]]
                          [:tbody
                           (let [agg-key (-> opts :agg-key keyword)
                                 agg-top (-> opts :agg-top keyword)]
                             (for [x (-> app :agg (get agg-key) :aggregations agg-top :buckets)]
                               [:tr
                                [:td (->> opts :header first :agg keyword (get x))]
                                (for [d (->> opts :header rest)]
                                  [:td (-> x (get-in [(keyword (:agg d)) :value]) str commify)])]))]]]
                        [:.card-reveal
                         [:span.card-title.grey-text.text-darken-4
                          (:agg-key opts)
                          [:i.mdi-navigation-close.right]
                          [:ul.collapsible.popout {:data-collapsible "accordion"}
                           [:li
                            [:div.collapsible-header [:i.fa.fa-trello] "Spec"]
                            [:div.collapsible-body
                             (om/build detail app {:opts opts})
                             ]]
                           [:li
                            [:div.collapsible-header [:i.fa.fa-list-ol] "Data"]
                            [:div.collapsible-body
                             (let [agg-key (-> opts :agg-key keyword)
                                   agg-top (-> opts :agg-top keyword)
                                   table-data (-> app :agg (get agg-key) :aggregations agg-top)]
;;                                (.log js/console (pr-str table-data))
                               (om/build detail app {:opts table-data}))
                             ]]
                           ]
                         ]]])))

(defcomponent aggregator [app owner opts]
  (will-mount [_]
              (go (while true
                    (let [agg-key (-> opts :agg-key keyword)
                          agg-resp (<! (es-agg (or (:url opts) (-> app :es-api :agg))
                                               (merge (:body opts)
                                                       {:query
                                                        {:filtered
                                                         {:query {:match_all {}}
                                                          :filter {:range {:_timestamp {:gte (or (:gte opts) "now-24h") :lte "now"}}}}}})))]
;;                       (.log js/console (pr-str agg-key))
;;                       (.log js/console (pr-str agg-resp))
                      (om/update! app [:agg agg-key] agg-resp))
                    (<! (timeout (or (-> app :poll-interval) poll-interval))))))
  (render [_] (html [:.hide "Aggregator:" (-> opts :agg-key)])))

;;;;;;;;;;;;;;;;;;
;; meta components

(defcomponent aggregators [app owner opts]
  (render [_] (html [:div (for [agg (:aggregators app)]
                            (om/build aggregator app {:opts agg}))])))

(defn- get-component
  [widget-type]
  (case widget-type
    "es-chart" es-chart
    "agg-table" agg-table
    "agg-summary" agg-summary
    "header" header
    nil))

(defn- build-component
  [app widget]
  (let [component (get-component (:type widget))
        cursor (-> widget :cursor keyword)]
    (if component (om/build component (if cursor (get app cursor) app) {:opts widget}))))

(defn- col-class
  [count-per-row]
  (case count-per-row
    1 "s12"
    2 "s6"
    3 "s4"
    4 "s3"
    "s3"))

(defn- build-row [app row]
  [:.row (for [widget row]
           [:div {:class (str "col " (col-class (count row)))}
            (build-component app widget)]
           )])

(defcomponent widgets [app owner opts]
  (render [_] (html [:div (for [row (:widgets app)] (build-row app row))])))



;;;;;;;;;;;;;;;;

(defn fetch-meta [url]
  (let [c (chan)]
    (go (let [{body :body} (<! (http/get url))]
          (>! c body)))
    c))

(defn init-app-state
  [state]
  (go (let [from-server (<! (fetch-meta "/_init"))]
        (om/update! state from-server)
        )))

;;;;;;;;;;;;;;;;

(defcomponent counter [data :- {:init js/Number} owner]
  (will-mount [_]
              (om/set-state! owner :n (:init data)))
  (render-state [_ {:keys [n]}]
                (html [:div
                       [:span (str "Count: " n)]
                       [:button
                        {:on-click #(om/set-state! owner :n (inc n))}
                        "+"]
                       [:button
                        {:on-click #(om/set-state! owner :n (dec n))}
                        "-"]])))

