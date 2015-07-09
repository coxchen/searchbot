(ns searchbot.widgets
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [put! <! >! chan timeout]]
            [searchbot.meta :refer [widget-wrapper detail]]
            [searchbot.charts :refer [es-chart]]
            [searchbot.search :refer [searchbox]]
            ))

(def poll-interval 30000)

;;;;;;;;;;;;;;;;;;;;

(defn- commify [s]
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- make-agg [agg-job]
  (let [match-all-query {:query {:match_all {}}}
        query (if-let [query-filter (or (-> agg-job :filter) (-> agg-job :default :filter))]
                {:query {:filtered (assoc match-all-query :filter query-filter)}}
                match-all-query)]
    (-> query (merge (:body agg-job)))))

(defn >agg [{:keys [url body agg-key] :as job} result-channel]
  (go (let [{agg :body} (<! (http/post url {:json-params (-> (make-agg job) (assoc :search_type "count"))}))]
        (>! result-channel {:agg-key (keyword agg-key) :resp agg}))))

(defcomponent the-aggregator [cursor owner opts]
  (will-mount [_]
              (.log js/console "# will mount the-aggregator")
              (go (while true
                    (let [job (<! (:req-chan (om/get-shared owner)))]
                      (>agg job (:resp-chan (om/get-shared owner))))))
              (go (while true
                    (let [resp (<! (:resp-chan (om/get-shared owner)))]
                      (om/update! cursor (:agg-key resp) (:resp resp))))))
  (render [this] (html [:.row (if (not (:show-summary cursor)) {:class "hide"}) [:.col.s4
                        [:.card.blue-grey.darken-1
                         [:.card-content.amber-text.text-accent-4
                          [:table.responsive-table.hoverable.bordered
                           [:thead
                            [:tr
                             [:th {:align "right"} "Aggregation"]
                             [:th {:align "right"} "Records aggregated"]
                             [:th {:align "right"} "Time (ms)"]]]
                           [:tbody
                            (for [aggKey (filter (fn [k] (not (some #(= k %) [:div :show-summary])))
                                                 (-> cursor keys))]
                              [:tr
                               [:td [:span (name aggKey)]]
                               [:td (-> cursor aggKey :hits :total str commify)]
                               [:td (-> cursor aggKey :took str commify)]])]]]]]]))
  (did-update [_ _ _]
              (.log js/console "# the-aggregator updated")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


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



;;;;;;;;;;;;;;;;;;;;

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
     {:card-title "black-text"
      :activator "white"
      :activator-icon "grey-text"})})

(defn- get-component
  [widget-type]
  (case widget-type
    "es-chart" {:_widget es-chart :_wrapper widget-wrapper}
    "agg-table" {:_widget agg-table :_wrapper widget-wrapper}
    "header" {:_widget header}
    "searchbox" {:_widget searchbox}
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
           [:div {:class (str "col " (or (:col widget) (col-class (count row))))}
            (build-component app widget)])])

(defcomponent widgets [app owner opts]
  (render [_] (html [:div (for [row (:widgets app)] (build-row app row))])))



;;;;;;;;;;;;;;;;


(defn >jobs [jobs req-chan settings]
  (go (doseq [job jobs]
        (let [job (assoc job :url (or (:url job) (:url-agg (settings)))
                             :default (:default (settings)))]
          (.log js/console "[>job]" (:agg-key job))
          (>! req-chan job)))))

(defn fetch-meta [url]
  (let [c (chan)]
    (go (let [{body :body} (<! (http/get url))]
          (>! c body)))
    c))

(defn init-app-state
  ([state] (init-app-state state nil))
  ([state view]
   (let [c (chan)]
     (go (let [init-base "/_init"
               init-url (if view (str init-base "/" view) init-base)
               from-server (<! (fetch-meta init-url))]
           (.log js/console (str "# _init app-state for " init-url))
           (.log js/console "# aggregators: " (pr-str (map :agg-key (:aggregators from-server))))
           (om/transact! state (fn [_] from-server))
           (>! c from-server)))
     c)))

(defcomponent navbar [cursor owner opts]
  (render [_]
          (html
           [:div (if (not (:nav cursor)) {:class "hide"})
            [:nav
             [:div.nav-wrapper
              [:ul.hide-on-med-and-down
               (for [nav (:nav cursor)]
                 [:li {:class (if (:active nav) "active")}
                  [:a {:href "#"
                       :on-click (fn [_]
                                   (.log js/console "[view]" (:view nav))
                                   (go (let [from-server (<! (init-app-state cursor (:view nav)))
                                             shared (om/get-shared owner)
                                             {req-chan :req-chan} shared
                                             {es-settings :es-settings} shared]
                                         (>jobs (:aggregators from-server) req-chan es-settings))))}
                   (:label nav)]])]]]])))
