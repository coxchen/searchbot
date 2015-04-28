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

(defroutes routes
  (resources "/react" {:root "react"})
  (GET "/" req (apply str (page)))
  (GET "/test" req
       (response {:foo "bar"}))
  (GET "/es/:idx/:idxType/_count" [idx idxType]
       (response (es/es-count @es-host idx idxType)))
  (POST "/es/:idx/:idxType/_search" [idx idxType :as req]
        (do
          (response (es/es-search @es-host idx idxType (:body req)))))
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
