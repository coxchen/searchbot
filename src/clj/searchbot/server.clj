(ns searchbot.server
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.tools.cli :refer [parse-opts]]
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
            [cheshire.core :as json]
            [searchbot.es :as es])
  (:gen-class))

(extend-protocol cheshire.generate/JSONable
  org.elasticsearch.common.joda.time.DateTime
  (to-json [t jg]
           (cheshire.generate/write-string jg (str t))))

(defn es-mode [mode] (dosync (ref-set es/ES_MODE mode)))

(defn- has-file?
  [filename]
  (.exists (io/file filename)))

(defn- read-edn
  [ednfile & {:keys [default]}]
  (let [ednfile (str ednfile ".edn")
        edn-content (if (has-file? ednfile)
                      (slurp ednfile)
                      (pr-str default))]
    (edn/read-string edn-content)))

(def ^:private server-default
  (atom {:es {:host "localhost" :port 9200 :cluster "elasticsearch"}
         :nav [{:view "app-state" :label "Default" :active true}]}))

(defn- load-server-edn [] (read-edn "server" :default @server-default))

(defn get-es-host [] (load-server-edn))

(deftemplate init-page (io/resource "index.html") []
  [:body] (if is-dev? inject-devmode-html identity))

(def default-app-state
  {:header-text "searchbot"
   :menu {:top-open? false :sub-open? false :selected {} :cm nil}
   :nav [{:view "app-state" :label "Dashboard" :active true}]
   :es-count 0
   :agg {:div {:width "90%" :height 300}}
   :aggregators []
   :widgets []})

(defn- activate-nav
  [navs active-view]
  (map (fn [nav] (if (= (:view nav) active-view)
                   (assoc nav :active true)
                   (assoc nav :active false)))
       navs))

(defn load-view-edn [view]
  (let [navs (:nav (load-server-edn))
        the-view (or view (:view (first navs)) "app-state")
        navs (activate-nav navs the-view)
        view-edn (read-edn the-view :default default-app-state)]
    (assoc view-edn :nav navs)))

(defroutes routes
  (resources "/react" {:root "react"})
  (GET "/" req (apply str (init-page)))
  (GET "/_init" req (response (load-view-edn nil)))
  (GET "/_init/:view" [view] (response (load-view-edn view)))
  (GET "/es/:idx/_count" [idx]
       (response (es/es-count (get-es-host) idx)))
  (POST "/es/:idx/_search" [idx :as req]
        (response (es/es-search (get-es-host) idx (:body req))))
  (GET "/es/:idx/:idxType/_count" [idx idxType]
       (response (es/es-count (get-es-host) idx idxType)))
  (POST "/es/:idx/:idxType/_search" [idx idxType :as req]
        (response (es/es-search (get-es-host) idx idxType (:body req))))
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
    (wrap-routes)))

(defn run-web-server [& {:keys [port]}]
  (let [port (Integer. (or port (env :port) 10555))]
    (print "# Starting web server on port" port "\n")
    (print "# Requests will be redirect to ES on" (get-es-host) "\n")
    (run-server http-handler {:port port :join? false})))

(defn run-auto-reload []
  (auto-reload *ns*)
  (start-figwheel))

(defn run [& {:keys [port]}]
  (when is-dev?
    (run-auto-reload))
  (run-web-server :port port))

(defn version
  "Print version for SearchBot and the current JVM."
  []
  (println "SearchBot" (System/getenv "SEARCHBOT_VERSION")
           "on Java" (System/getProperty "java.version")
           (System/getProperty "java.vm.name")))

(defn print-help [summary]
  (println "Usage: searchbot [-p 10555] command args\n")
  (println "# Options available:")
  (println summary)
  (println "----------")
  (println "# Commands available:")
  (println " run      Run SearchBot.")
  (println " version  Print SearchBot version.")
  (println " upgrade  Upgrade SearchBot to a latest version."))

(def cli-opts
  [["-p" "--port PORT" "Port used for SearchBot API server."
    :id :port
    :default 10555
    :parse-fn #(Integer/parseInt %)]
   ["-n" nil "Running with NATIVE mode"
    :id :native :default false
    :assoc-fn (fn [m k _] (update-in m [k] not))]])

(defn -main [& args]
  (let [{:keys [options arguments summary]} (parse-opts args cli-opts)
        {:keys [port native]} options
        cmd (first arguments)
        args (next arguments)]
    (when native (es-mode :NATIVE))
    (case cmd
      "run" (do (version) (run :port port))
      "version" (version)
      (print-help summary))))
