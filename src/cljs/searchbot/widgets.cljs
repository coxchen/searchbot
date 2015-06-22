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
                                          (-> post-body
                                              (assoc :search_type "count")
                                              (assoc :preference "_local")
                                              )}))]
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
                          [:table.responsive-table.hoverable.bordered
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

(defcomponent aggregator [app owner opts]
  (will-mount [_]
              (go (while true
                    (let [agg-key (-> opts :agg-key keyword)
                          agg-resp (<! (es-agg (or (:url opts) (-> app :es-api :agg))
                                               (merge (:body opts)
                                                       {:query
                                                        {:filtered
                                                         {:query {:match_all {}}
                                                          :filter {:range
                                                                   {:_timestamp {:gte (or (:gte opts) "now-24h") :lte "now"}}}}}})))]
;;                       (.log js/console (pr-str agg-key))
;;                       (.log js/console (pr-str agg-resp))
                      (om/update! app [:agg agg-key] agg-resp))
                    (<! (timeout (or (-> app :poll-interval) poll-interval))))))
  (render [_] (html [:.hide "Aggregator:" (-> opts :agg-key)])))

(defcomponent aggregators [app owner opts]
  (render [_] (html [:div (for [agg (:aggregators app)]
                            (om/build aggregator app {:opts agg}))])))

(defcomponent agg-table [cursor owner opts]
  (render [this] (html [:table.responsive-table.hoverable.bordered
                        [:thead
                         [:tr
                          (for [col (:header opts)]
                            [:th (:label col)])]]
                        [:tbody
                         (let [agg-key (-> opts :agg-key keyword)
                               agg-top (-> opts :agg-top keyword)]
                           (for [x (-> cursor (get agg-key) :aggregations agg-top :buckets)]
                             [:tr
                              [:td (->> opts :header first :agg keyword (get x))]
                              (for [d (->> opts :header rest)]
                                [:td (-> x (get-in [(keyword (:agg d)) :value]) str commify)])]))]])))

;;;;;;;;;;;;;;;;;;
;; meta components

(defcomponent detail [app owner opts]
  (render [_]
          (html
           [:pre {:style {:font-size "8pt"}}
            [:code.json (.stringify js/JSON (clj->js opts) nil 4)]])))

(defcomponent widget-wrapper [cursor owner opts]
  (render [this] (html [:.card {:class (-> opts :_theme :card)}
                        [:.card-content {:class (-> opts :_theme :card-content)}
                         [:span.card-title {:class (-> opts :_theme :card-title)} (-> opts :agg-key name)
                          [:a.btn-floating.btn-flat.waves-effect.activator.right {:class (-> opts :_theme :activator)}
                           [:i.mdi-action-settings {:class (-> opts :_theme :activator-icon)}]]]
                         (om/build (-> opts :_widget) cursor {:opts opts})]
                        [:.card-reveal
                         [:span.card-title.grey-text.text-darken-4 (:agg-key opts)
                          [:i.mdi-navigation-close.right]
                          [:ul.collapsible {:data-collapsible "accordion"}
                           [:li
                            [:div.collapsible-header [:i.fa.fa-trello] "Spec"]
                            [:div.collapsible-body (om/build detail cursor {:opts opts})]]
                           [:li
                            [:div.collapsible-header [:i.fa.fa-list-ol] "Data"]
                            [:div.collapsible-body
                             (let [agg-key (-> opts :agg-key keyword)
                                   agg-top (-> opts :agg-top keyword)
                                   widget-data (-> cursor (get agg-key) :aggregations agg-top)]
                               (om/build detail cursor {:opts widget-data}))]]]]]])))

(defn- widget-theme
  [widget-type]
  {:_theme
   (case widget-type
     "es-chart"  {:card-title "black-text"
                  :activator "white"
                  :activator-icon "grey-text"}
     "agg-table" {:card "blue-grey darken-1"
                  :card-content "amber-text text-accent-4"
                  :activator "waves-light"}
     {})})

(defn- get-component
  [widget-type]
  (case widget-type
    "es-chart" {:_widget es-chart :_wrapper widget-wrapper}
    "agg-table" {:_widget agg-table :_wrapper widget-wrapper}
    "agg-summary" {:_widget agg-summary}
    "header" {:_widget header}
    nil))

(defn- build-component
  [app widget]
  (let [component (get-component (:type widget))
        cursor (-> widget :cursor keyword)
        cursor-data (if cursor (get app cursor) app)]
    (if component
      (if (:_wrapper component)
        (om/build widget-wrapper cursor-data {:opts (merge widget component (widget-theme (:type widget)))})
        (om/build (:_widget component) cursor-data {:opts widget})))))

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
            (build-component app widget)])])

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
        (om/update! state from-server))))

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

