(ns searchbot.widgets
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [put! <! >! chan timeout]]
            [searchbot.charts :refer [es-chart]]))


(defn es-url [purpose]
  (case purpose
    :count "/es/avc_1d/avc/_count"
    :agg "/es/avc_1d/avc/_search"
    "/es"))

(def poll-interval 30000)

(defn commify [s]
  (let [s (reverse s)]
    (loop [[group remainder] (split-at 3 s)
           result '()]
      (if (empty? remainder)
        (apply str (reverse (rest (concat result (list \,) group)))) ; rest to remove extraneous ,
        (recur (split-at 3 remainder) (concat result (list \,) group))))))


(defn es-count []
  (let [c (chan)]
    (go (let [{{avcCount :count} :body} (<! (http/get (es-url :count)))]
          (>! c avcCount)))
    c))

(defn es-agg [post-body]
  (let [c (chan)]
    (go (let [{agg :body} (<! (http/post (es-url :agg) {:json-params post-body}))]
          (>! c agg)))
    c))

(defcomponent header [app owner opts]
  (will-mount [_]
              (go (while true
                    (let [avcCount (<! (es-count))]
                      (om/update! app [:es-count] avcCount))
                    (<! (timeout poll-interval)))))
  (render [_]
          (html [:h2 (:header-text app) " with " (-> app :es-count str commify) " raw data in ES"])))

(defcomponent agg-summary [app owner opts]
  (render [this] (html [:table.table.table-hover.table-condensed
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
                            [:td (-> app :agg aggKey :took str commify)]])]])))

(defcomponent agg-table [app owner opts]
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
                                [:td (->> opts :header first :agg keyword (get x))]
                                (for [d (->> opts :header rest)]
                                  [:td (-> x (get-in [(keyword (:agg d)) :value]) str commify)])]))]]]])))

(defcomponent aggregator [app owner opts]
  (will-mount [_]
              (go (while true
                    (let [agg-key (-> opts :agg-key keyword)
                          agg-resp (<! (es-agg (merge (:body opts)
                                                       {:query
                                                        {:filtered
                                                         {:query {:match_all {}}
                                                          :filter {:range {:timestamp {:gte "now-12h" :lte "now"}}}}}})))]
;;                       (.log js/console (pr-str agg-key))
;;                       (.log js/console (pr-str agg-resp))
                      (om/update! app [:agg agg-key] agg-resp))
                    (<! (timeout poll-interval)))))
  (render [_] (html [:.hide "Aggregator:" (-> opts :agg-key)])))

;;;;;;;;;;;;;;;;;;
;; meta components

(defn fetch-meta [url]
  (let [c (chan)]
    (go (let [{body :body} (<! (http/get url))]
          (>! c body)))
    c))

(defcomponent aggregators [app owner opts]
;;   (will-mount [_]
;;               (go (while true
;;                     (let [{_aggs :aggregators} (<! (fetch-meta "/_aggregators"))]
;;                       (om/update! app [:aggregators] _aggs))
;;                     (<! (timeout poll-interval)))))
  (render [_] (html [:div (for [agg (:aggregators app)] (om/build aggregator app {:opts agg}))])))

(defn- get-component
  [widget-type]
  (case widget-type
    "es-chart" es-chart
    "agg-table" agg-table
    nil))

(defn- build-component
  [app widget]
  (let [component (get-component (:type widget))
        cursor (-> widget :cursor keyword)]
    (if component (om/build component (if cursor (get app cursor) app) {:opts widget}))))

(defn- col-class
  [count-per-row]
  (case count-per-row
    1 :.col-lg-12
    2 :.col-lg-6
    3 :.col-lg-4
    4 :.col-lg-3
    :.col-lg-3))

(defn- build-row [app row]
  [:.row (for [widget row]
           [(col-class (count row)) (build-component app widget)])])

(defcomponent widgets [app owner opts]
;;   (will-mount [_]
;;               (go (while true
;;                     (let [{_widgets :widgets} (<! (fetch-meta "/_widgets"))]
;;                       (om/update! app [:widgets] _widgets))
;;                     (<! (timeout poll-interval)))))
  (render [_] (html [:div (for [row (:widgets app)] (build-row app row))])))


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

;;;;;;;;;;;;;;;;
;; widget editor


(defn- class-names [elem] (-> elem .-className (clojure.string/split #" ") set))

(defn- add-class! [elem a-class-name]
  (let [names (class-names elem)]
    (aset elem "className" (clojure.string/join " " (conj names a-class-name)))))

(defn- remove-class! [elem a-class-name]
  (let [names (class-names elem)]
    (aset elem "className" (clojure.string/join " " (disj names a-class-name)))))

(defn- toggle-sub-menu
  [app & widget]
  (let [sub-open? (get-in @app [:menu :sub-open?])]
    (if sub-open?
      (remove-class! js/document.body "show-submenu")
      (add-class! js/document.body "show-submenu"))
    (om/update! app [:menu :sub-open?] (not sub-open?))
    (if widget (do
                 (om/update! app [:menu :selected] (first widget))))))

(defn- toggle-top-menu
  [app]
  (let [top-open? (get-in @app [:menu :top-open?])]
    (if top-open?
      (remove-class! js/document.body "show-menu")
      (add-class! js/document.body "show-menu"))
    (om/update! app [:menu :top-open?] (not top-open?))))

(defn within? [elem-id]
  (fn [ev]
    (let [target (.-target ev)]
      (-> (js/$ target) (.closest elem-id) .-length pos?))))


(defn- widget-class
  [widget]
  (case (:type widget)
    "es-chart" (case (:draw-fn widget)
                 "draw-line" "fa fa-fw fa-line-chart"
                 "draw-ring" "fa fa-fw fa-pie-chart")
    "agg-table" "fa fa-fw fa-table"))

(defcomponent widget-detail [app owner opts]
  (render [_]
          (html [:textarea#widget-detail
                 {:rows "20" :cols "60"
                  :value (.stringify js/JSON (clj->js (-> @app :menu :selected)) nil 4)}]))
  (did-update [_ _ _]
              (let [cm (-> @app :menu :cm)]
                (.setValue cm (.stringify js/JSON (clj->js (-> @app :menu :selected)) nil 4))
                ))
  (did-mount [_]
             (let [cm (.fromTextArea js/CodeMirror
                                     (.getElementById js/document "widget-detail")
                                     {:lineNumbers true
                                      :mode: "javascript"})]
               (om/update! app [:menu :cm] cm))))


(defcomponent widgets-grid [app owner opts]
  (will-mount
   [_]
   (.addEventListener js/window
                      "mousedown" (fn [ev]
                                    (let [target (.-target ev)
                                          sub-open? (get-in @app [:menu :sub-open?])
                                          close-sub? (and sub-open? (not ((within? "#sub-menu") ev)))]
                                      (if close-sub? (toggle-sub-menu app))))))
  (render [_]
          (html [:div.grid-content.grid-five-rows
                 [:div.grid-head
                  [:div "COL 1"]
                  [:div "COL 2"]
                  [:div "COL 3"]
                  [:div "COL 4"]]
                 [:div.grid-body
                  (for [widget-row (:widgets app)]
                    [:div.grid-row
                     (for [w widget-row]
                       [:div {:on-click #(toggle-sub-menu app w)}
                        [:span.grid-cell [:i {:class (widget-class w)}]]])])
                  ]])))


(defcomponent off-canvas [app owner opts]
  (will-mount
   [_]
   (.addEventListener js/window
                      "mousedown" (fn [ev]
                                    (let [target (.-target ev)
                                          top-open? (get-in @app [:menu :top-open?])
                                          close-top? (and top-open?
                                                          (not (or ((within? "#top-menu") ev)
                                                                   ((within? "#sub-menu") ev))))]
                                      (if close-top? (toggle-top-menu app))))))
  (render [_]
          (html [:div.container
                 [:div#top-menu.menu-wrap {:data-level "1" }
                  [:nav.menu
                   [:h2 [:span "Widgets"]]
                   [:div#top-menu-body.grid-container]]
                  ]
                 [:div#sub-menu.menu-wrap {:data-level "2" }
                  [:nav.menu
                   [:h2 "Widget Design"]
                   (om/build widget-detail app)
                   ]
                  ]
                 [:button#open-button.menu-button {:on-click #(toggle-top-menu app)}
                  [:i.fa.fa-fw.fa-cogs]
                  [:span "Open Menu"]]
                 [:div.content-wrap
                  [:div.content
                   [:div.container-fluid {:style {:max-width "90%" :padding-left 10}}
                    (om/build (:content opts) app)
                    ]
                   ]]
                 ])))

