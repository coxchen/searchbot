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

;;;;;;;;;;;;;;;;;;
;; meta components

(defcomponent detail [app owner opts]
  (render [_]
          (html
           [:pre {:style {:font-size "8pt"}}
            [:code.json {:ref "detail"} (.stringify js/JSON (clj->js opts) nil 4)]]))
  (did-update [_ _ _]
              (.log js/console "# updating detail" (pr-str (keys opts)))
              (.log js/console (om/get-node owner "detail"))
              (-> (om/get-node owner "detail")
                  (js/hljs.highlightBlock)))
  )

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
                          [:ul.collapsible {:ref "wrapper-collapsible" :data-collapsible "accordion"}
                           [:li
                            [:div.collapsible-header [:i.fa.fa-trello] "Spec"]
                            [:div.collapsible-body (om/build detail cursor {:opts opts})]]
                           [:li
                            [:div.collapsible-header [:i.fa.fa-list-ol] "Data"]
                            [:div.collapsible-body
                             (let [agg-key (-> opts :agg-key keyword)
                                   agg-top (-> opts :agg-top keyword)
                                   widget-data (-> cursor (get agg-key) :aggregations agg-top)]
                               (om/build detail cursor {:opts widget-data}))]]]]]]))
  (did-mount [_]
             (-> (om/get-node owner "wrapper-collapsible")
                 (js/$)
                 (.collapsible (clj->js {:accordion false}))))
  )

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
  (merge (:body agg-job)
         {:query
          {:filtered
           {:query {:match_all {}}
            :filter {:range
                     {:_timestamp {:gte (or (:gte agg-job) "now-24h") :lte "now"}}}}}}))

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
  (render [this] (html [:.row [:.col.s4
                        [:.card.blue-grey.darken-1
                         [:.card-content.amber-text.text-accent-4
                          [:table.responsive-table.hoverable.bordered
                           [:thead
                            [:tr
                             [:th {:align "right"} "Aggregation"]
                             [:th {:align "right"} "Records aggregated"]
                             [:th {:align "right"} "Time (ms)"]]]
                           [:tbody
                            (for [aggKey (filter #(not= :div %) (-> cursor keys))]
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

;;;;;;;;;;;;;
;; searchbox

(defn es-query [api-url post-body]
  (let [c (chan)]
    (go (let [{resp :body} (<! (http/post api-url {:json-params (-> post-body)}))]
          (>! c resp)))
    c))

(defn- update-searchbox
  [owner inputValue]
  (let [newQuery (if (> (count inputValue) 0)
                   {:query {:query_string {:query inputValue}}}
                   {:query {:match_all {}}})]
    (om/set-state! owner :query newQuery)
    (om/set-state! owner :query-string inputValue)))

(defn- update-query-result
  [owner result]
  (om/set-state! owner :query-result result))

(defcomponent searchbox [cursor owner opts]
  (will-mount [_]
              (update-searchbox owner "")
              (update-query-result owner {}))
  (render-state [_ {:keys [query query-string query-result]}]
                (let [reference "query-string"]
                  (html [:.card
                         [:.card-content
                          [:span.card-title.black-text "searching in "
                           [:strong (str "/" (:es-index opts) "/" (:es-type opts))]
                           [:a.btn-floating.btn-flat.waves-effect.activator.white.right
                            [:i.mdi-action-settings.grey-text]]]
                          [:.input-field
                           [:i.prefix.fa.fa-search]
                           [:input {:id (:id opts)
                                    :ref (:id opts)
                                    :value query-string
                                    :type "text"
                                    :on-change (fn [x]
                                                 (let [thisNode (om/get-node owner (:id opts))
                                                       inputValue (.-value thisNode)]
                                                   (update-searchbox owner inputValue)))
                                    :on-key-down (fn [e]
                                                   (let [keyCode (-> e .-keyCode)]
                                                     (case keyCode
                                                       27 (do ;; ESC
                                                            (update-searchbox owner "")
                                                            (update-query-result owner {}))
                                                       13 (go ;; ENTER
                                                           (let [resp (<! (es-query (str "/es/"
                                                                                         (:es-index opts) "/"
                                                                                         (:es-type opts) "/_search")
                                                                                    query))]
                                                             (update-query-result owner resp)))
                                                       nil)))}]
                           [:label {:for (:id opts)} "Query String"]]
                          [:div "query"
                           [:pre {:style {:font-size "8pt"}}
                            [:code.json {:ref "current-query-string"} (.stringify js/JSON (clj->js query) nil 4)]]]
                          [:div (str "resp: " (or (count (get-in query-result [:hits :hits])) 0) "/"
                                     (or (get-in query-result [:hits :total]) 0) " hits")
                           [:table.responsive-table.hoverable.bordered
                            [:thead
                             [:tr
                              (for [col (:header opts)]
                                [:th (:label col)])]]
                            [:tbody
                             (let [hits (get-in query-result [:hits :hits])]
                               (for [hit hits]
                                 [:tr
                                  (for [col (:header opts)]
                                    [:td (-> hit :_source (get (-> col :field keyword)))])]))]]]]
                         [:.card-reveal
                          [:span.card-title.grey-text.text-darken-4 "metadata"
                           [:i.mdi-navigation-close.right]
                           [:ul.collapsible {:data-collapsible "accordion"}
                            [:li
                             [:div.collapsible-header [:i.fa.fa-trello] "Spec"]
                             [:div.collapsible-body (om/build detail cursor {:opts opts})]]
                            [:li
                             [:div.collapsible-header [:i.fa.fa-list-ol] "Response"]
                             [:div.collapsible-body
                              [:pre {:style {:font-size "8pt"}}
                               [:code.json {:ref "query-result"} (.stringify js/JSON (clj->js query-result) nil 4)]]
                              ]]]]]
                         ])))
  (did-update [_ _ _]
              (let [input (om/get-node owner "current-query-string")
                    result (om/get-node owner "query-result")]
                (.log js/console "highlight!")
                (js/hljs.highlightBlock input)
                (js/hljs.highlightBlock result)
                )))

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
     {})})

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


(defn >jobs [jobs req-chan urls]
  (go (doseq [job jobs]
        (let [job (if (:url job)
                    job
                    (assoc job :url (:agg (urls))))]
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
           [:div
            [:nav
             [:div.nav-wrapper
              [:ul.hide-on-med-and-down
               (for [nav (:nav cursor)]
                 [:li {:class (if (:active nav) "active")}
                  [:a {:href "#"
                       :on-click (fn [_]
                                   (.log js/console (:view nav))
                                   (go (let [from-server (<! (init-app-state cursor (:view nav)))
                                             shared (om/get-shared owner)
                                             {req-chan :req-chan} shared
                                             {es-urls :es-urls} shared]
                                         (>jobs (:aggregators from-server) req-chan es-urls))))}
                   (:label nav)]])]]]])))
