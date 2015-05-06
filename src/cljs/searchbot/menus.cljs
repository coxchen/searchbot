(ns searchbot.menus
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]))

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