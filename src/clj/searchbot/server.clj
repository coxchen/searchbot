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

(defn- has-file?
  [filename]
  (.exists (io/file filename)))

(defn- read-edn
  [ednfile & {:keys [default]}]
  (if (has-file? ednfile)
    (slurp ednfile)
    default))

;; (def ^:private es-host (atom (str "http://127.0.0.1:9200")))
(def ^:private es-host
  (atom (str "http://" (or (:es-host (clojure.edn/read-string (read-edn "server.edn")))
                           "127.0.0.1") ":9200")))

(deftemplate init-page (io/resource "index.html") []
  [:body] (if is-dev? inject-devmode-html identity))

(def default-app-state
  {:header-text "searchbot"
   :menu {:top-open? false :sub-open? false :selected {} :cm nil}
   :es-count 0
   :agg {:div {:width "90%" :height 300}}
   :aggregators []
   :widgets []})

(defroutes routes
  (resources "/react" {:root "react"})
  (GET "/" req (apply str (init-page)))
  (GET "/_init" req (response (clojure.edn/read-string
                               (read-edn "app-state.edn" :default (pr-str default-app-state)))))
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

(defn wrap-routes []
  (-> routes
      (mid-json/wrap-json-body {:keywords? true})
      (mid-json/wrap-json-response)
      (wrap-defaults api-defaults)))

(def http-handler
  (if is-dev?
    (reload/wrap-reload (wrap-routes))
;;     (wrap-defaults routes api-defaults)))
    (wrap-routes)))

(defn run-web-server [& {:keys [port host]}]
  (let [port (Integer. (or port (env :port) 10555))]
    (if host (reset! es-host (str "http://" host ":9200")))
    (print "# Starting web server on port" port "\n")
    (print "# Requests will be redirect to ES on" @es-host "\n")
    (run-server http-handler {:port port :join? false})))

(defn run-auto-reload []
  (auto-reload *ns*)
  (start-figwheel))

(defn run [& {:keys [port host]}]
  (when is-dev?
    (run-auto-reload))
  (run-web-server :port port :host host))

(defn -main [& [port]]
  (run :port port))
