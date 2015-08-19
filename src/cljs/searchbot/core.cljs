(ns searchbot.core
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [om.core :as om :include-macros true]
;;             [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [cljs-http.client :as http]
            [goog.events :as events]
            [cljs.core.async :refer [put! <! >! chan timeout]]
            [searchbot.widgets :refer [the-aggregator widgets init-app-state navbar >jobs]]
            [searchbot.menus :refer [off-canvas widgets-grid widget-detail]]))

(defonce app-state (atom {:aggregators []
                          :agg {}}))

(defn- ref-state [k] (if-let [c (k (om/root-cursor app-state))] (om/ref-cursor c)))
(defn- agg-jobs [] (om/ref-cursor (:aggregators (om/root-cursor app-state))))
(defn- es-settings [] (om/ref-cursor (:es-settings (om/root-cursor app-state))))

(defcomponent my-app [app owner]
  (will-mount [_]
              (go (let [{jobs :aggregators} (<! (init-app-state app))]
                    (>jobs jobs (:req-chan (om/get-shared owner)) ref-state es-settings))))
  (render [_] (html [:div
                     (om/build navbar app)
                     (om/build widgets app)]))
  (did-update [_ _ _]
              (.log js/console "# updating my-app")))


(defn main []
;;   (om/root my-app app-state {:target js/document.body})
  (let [req-chan (chan)
        resp-chan (chan)]
    (go (while true
          (let [jobs (agg-jobs)]
            (.log js/console (str (count jobs) " jobs: " (map :agg-key jobs)))
            (>jobs jobs req-chan ref-state es-settings)
            (<! (timeout (or (:poll-interval @app-state) 10000))))))

    (om/root off-canvas
             app-state
             {:target js/document.body
              :opts {:content my-app
                     :top-menu {:content widgets-grid :header "Widgets"}
                     :sub-menu {:content widget-detail :header "Widget Design"}}
              :shared {:req-chan req-chan
                       :resp-chan resp-chan
                       :es-settings es-settings
                       :ref-state ref-state}})
    ))
