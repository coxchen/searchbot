(ns searchbot.menus
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]))

;;;;;;;;;;;;;;;;;;
;; off-canvas menu


(defn- class-names [elem] (-> elem .-className (clojure.string/split #" ") set))

(defn- add-class! [elem a-class-name]
  (let [names (class-names elem)]
    (aset elem "className" (clojure.string/join " " (conj names a-class-name)))))

(defn- remove-class! [elem a-class-name]
  (let [names (class-names elem)]
    (aset elem "className" (clojure.string/join " " (disj names a-class-name)))))

(defn- elem
  ([] js/document.body)
  ([id] (.getElementById js/document id)))

(defn- toggle-sub-menu
  [app]
  (let [sub-open? (get-in @app [:menu :sub-open?])]
    (if sub-open?
      (remove-class! (elem "off-canvas") "show-submenu")
      (add-class! (elem "off-canvas") "show-submenu"))
    (om/update! app [:menu :sub-open?] (not sub-open?))))

(defn- toggle-top-menu
  [app]
  (let [top-open? (get-in @app [:menu :top-open?])]
    (if top-open?
      (remove-class! (elem "off-canvas") "show-menu")
      (add-class! (elem "off-canvas") "show-menu"))
    (om/update! app [:menu :top-open?] (not top-open?))))

(defn within? [elem-id]
  (fn [ev]
    (let [target (.-target ev)]
      (-> (js/$ target) (.closest elem-id) .-length pos?))))


(defcomponent off-canvas [app owner opts]
  (will-mount
   [_]
   (.addEventListener js/window
                      "mousedown" (fn [ev]
                                    (let [close-top? (and (get-in @app [:menu :top-open?])
                                                          (not (or ((within? "#top-menu") ev)
                                                                   ((within? "#sub-menu") ev))))
                                          close-sub? (and (get-in @app [:menu :sub-open?])
                                                          (not ((within? "#sub-menu") ev)))]
                                      (if close-top? (toggle-top-menu app))
                                      (if close-sub? (toggle-sub-menu app))))))
  (render [_]
          (html [:div#off-canvas.base
                 [:div#top-menu.menu-wrap {:data-level "1" }
;;                   [:nav.menu
                   [:h2 (-> opts :top-menu :header)]
                   (om/build (-> opts :top-menu :content) app)
;;                    ]
                  ]
                 [:div#sub-menu.menu-wrap {:data-level "2" }
                  [:nav.menu
                   [:h2 (-> opts :sub-menu :header)]
                   (om/build (-> opts :sub-menu :content) app)]]
                 [:button.menu-button {:on-click #(toggle-top-menu app)}
                  [:i.fa.fa-fw.fa-cogs]
                  [:span "Open Menu"]]
                 [:div.content-wrap
                  [:div.content
                   [:div {:style {:max-width "90%" :padding-left "100px" :padding-top "20px"}}
                    (om/build (:content opts) app)]]]])))

;;;;;;;;;;;;;;;;
;; menu contents

(defn- widget-class
  [widget]
  (case (:type widget)
    "es-chart" (case (:draw-fn widget)
                 "draw-line" "fa fa-fw fa-line-chart"
                 "draw-ring" "fa fa-fw fa-pie-chart"
                 "draw-bar" "fa fa-fw fa-bar-chart")
    "agg-table" "fa fa-fw fa-table"
    "fa fa-fw fa-cubes"))

(defcomponent widgets-grid [app owner opts]
  (render [_]
          (html [:div.grid-base.grey.lighten-5
                 [:div.grid-content.grid-five-rows
                  [:div.grid-head
                   [:div "COL 1"]
                   [:div "COL 2"]
                   [:div "COL 3"]
                   [:div "COL 4"]]
                  [:div.grid-body
                   (for [widget-row (:widgets app)]
                     [:div.grid-row
                      (for [w widget-row]
                        [:div {:on-click #(do
                                            (toggle-sub-menu app)
                                            (om/update! app [:menu :selected] w))}
                         [:span.grid-cell [:i {:class (widget-class w)}]]])])]]])))

(defcomponent widget-detail [app owner opts]
  (render [_]
          (html [:textarea#widget-detail
                 {:rows "20" :cols "60"
                  :value (.stringify js/JSON (clj->js (-> @app :menu :selected)) nil 4)}]))
  (did-update [_ _ _]
              (if-let [cm (-> @app :menu :cm)]
                (.setValue cm (.stringify js/JSON (clj->js (-> @app :menu :selected)) nil 4))
                ))
  (did-mount [_]
             (let [cm (.fromTextArea js/CodeMirror
                                     (.getElementById js/document "widget-detail")
                                     {:lineNumbers true
                                      :mode "javascript"})]
               (om/update! app [:menu :cm] cm))))
