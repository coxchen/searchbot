(ns searchbot.server
  (:require [clojure.java.io :as io]
            [searchbot.dev :refer [is-dev? inject-devmode-html browser-repl start-figwheel]]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [resources not-found]]
            [net.cgrand.enlive-html :refer [deftemplate]]
            [net.cgrand.reload :refer [auto-reload]]
            [ring.middleware.reload :as reload]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults site-defaults]]
            [ring.middleware.json :as mid-json]
            [ring.util.response :refer [resource-response response redirect]]
            [ring.util.json-response :as jresp]
            [environ.core :refer [env]]
            [org.httpkit.server :refer [run-server]]
            [searchbot.es :as es]))

(def ^:private es-host (atom (str "http://172.18.114.55:9200")))

(deftemplate page (io/resource "index.html") []
  [:body] (if is-dev? inject-devmode-html identity))

(def aggregators
  {:aggregators [{:agg-key "SSID AGG"
                  :body {:aggs {:ssids
                                {:terms {:field "ssid" :order {:sum_usage "desc"}}
                                 :aggs {:sum_usage {:sum {:field "usage"}}
                                        :client_count
                                        {:cardinality {:field "client_mac.hash"
                                                       :precision_threshold 50000}}}}}}}
                 {:agg-key "APP AGG"
                  :body {:aggs {:apps
                                {:terms {:field "app" :order {:sum_usage "desc"}}
                                 :aggs {:sum_usage {:sum {:field "usage"}}
                                        :sum_up {:sum {:field "up"}}
                                        :sum_down {:sum {:field "down"}}}}}}}
                 {:agg-key "TIME AGG"
                  :body {:aggs {:traffic_over_time
                                {:date_histogram
                                 {:field "timestamp" :interval "1h"
                                  :format "MM-dd kk:mm" :post_zone "+08:00"}
                                 :aggs {:sum_up {:sum {:field "up"}}
                                        :sum_down {:sum {:field "down"}}}}}}}
                 ]})

(def widgets
  {:widgets [[{:type :es-chart :cursor :agg
               :id "ssid_pie" :agg-key "SSID AGG" :agg-top "ssids"
               :agg-view [:key :sum_usage]
               :draw-fn :draw-ring
               :chart {:bounds {:x "5%" :y "15%" :width "80%" :height "80%"}
                       :plot :pie
                       :p-axis "sum_usage"
                       :c-axis "key"}}
              {:type :es-chart :cursor :agg
               :id "app_pie" :agg-key "APP AGG" :agg-top "apps"
               :agg-view [:key :sum_usage]
               :draw-fn :draw-ring
               :chart {:bounds {:x "5%" :y "15%" :width "80%" :height "80%"}
                       :plot :pie
                       :p-axis "sum_usage"
                       :c-axis "key"}}
              {:type :es-chart :cursor :agg
               :id "time_line" :agg-key "TIME AGG" :agg-top "traffic_over_time"
               :agg-view [:key_as_string :sum_up :sum_down]
               :draw-fn :draw-line :trans :trans-line
               :chart {:bounds {:x "10%" :y "5%" :width "80%" :height "70%"}
                       :plot :line
                       :x-axis "key_as_string"
                       :y-axis "value"
                       :c-axis "type"}}
              ]
             [{:type :agg-table
               :agg-key "SSID AGG" :agg-top "ssids"
               :header [{:label "SSID" :agg :key}
                        {:label "Client Count" :agg :client_count}
                        {:label "Usage" :agg :sum_usage}]}
              {:type :agg-table
               :agg-key "APP AGG" :agg-top "apps"
               :header [{:label "Application" :agg :key}
                        {:label "Usage" :agg :sum_usage}
                        {:label "UpLink" :agg :sum_up}
                        {:label "DownLink" :agg :sum_down}]}
              {:type :agg-table
               :agg-key "TIME AGG" :agg-top "traffic_over_time"
               :header [{:label "TIME" :agg :key_as_string}
                        {:label "UpLink" :agg :sum_up}
                        {:label "DownLink" :agg :sum_down}]}
              ]]})


(defroutes routes
  (resources "/react" {:root "react"})
  (GET "/" req (apply str (page)))
  (GET "/_aggregators" req (response aggregators))
  (GET "/_widgets" req (response widgets))
  (GET "/test" req (response {:foo "bar"}))
  (GET "/es/:idx/:idxType/_count" [idx idxType]
       (response (es/es-count @es-host idx idxType)))
  (POST "/es/:idx/:idxType/_search" [idx idxType :as req]
        (response (es/es-search @es-host idx idxType (:body req))))
  (resources "/")
  (not-found "Not Found"))

;; (def http-handler
;;   (if is-dev?
;;     (reload/wrap-reload (wrap-defaults #'routes api-defaults))
;;     (wrap-defaults routes api-defaults)))

(def http-handler
  (if is-dev?
    (reload/wrap-reload
     (-> routes
         (mid-json/wrap-json-body {:keywords? true})
         (mid-json/wrap-json-response)
         (wrap-defaults api-defaults)))
    (wrap-defaults routes api-defaults)))

(defn run-web-server [& [port]]
  (let [port (Integer. (or port (env :port) 10555))]
    (print "Starting web server on port" port ".\n")
    (run-server http-handler {:port port :join? false})))

(defn run-auto-reload [& [port]]
  (auto-reload *ns*)
  (start-figwheel))

(defn run [& [port]]
  (when is-dev?
    (run-auto-reload))
  (run-web-server port))

(defn -main [& [port]]
  (run port))
