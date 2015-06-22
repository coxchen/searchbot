(ns searchbot.core
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [om.core :as om :include-macros true]
;;             [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [cljs-http.client :as http]
            [goog.events :as events]
            [cljs.core.async :refer [put! <! >! chan timeout]]
            [searchbot.widgets :refer [aggregators widgets init-app-state]]
            [searchbot.menus :refer [off-canvas widgets-grid widget-detail]]))

(defonce app-state (atom{}))

(defcomponent my-app [app owner]
  (will-mount [_]
              (init-app-state app))
  (render [_] (html [:div
                     (om/build aggregators app)
                     (om/build widgets app)])))

(defn main []
;;   (om/root my-app app-state {:target js/document.body})
  (om/root off-canvas
           app-state
           {:target js/document.body
            :opts {:content my-app
                   :top-menu {:content widgets-grid :header "Widgets"}
                   :sub-menu {:content widget-detail :header "Widget Design"}}})
  )
