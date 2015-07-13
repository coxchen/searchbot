(ns searchbot.meta
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]))

;;;;;;;;;;;;;;;;;;
;; meta components

;; (defn- rainbow [node]
;;   (.color js/Rainbow node))

(defn- log [msg]
  (.log js/console msg))

(defcomponent detail [cursor owner opts]
  (render [_]
          (html
           [:pre {:style {:font-size "8pt"}}
            [:code.language-javascript {:ref "detail"} (.stringify js/JSON (clj->js cursor) nil 4)]]))
  (did-update [_ _ _]
              (->> (om/get-node owner "detail")
                   (.highlightElement js/Prism)))
  (did-mount [_]
             (->> detail-node (om/get-node owner "detail")
                  (.highlightElement js/Prism))))

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
                            [:div.collapsible-body (om/build detail opts)]
                            ]
                           [:li
                            [:div.collapsible-header [:i.fa.fa-list-ol] "Data"]
                            [:div.collapsible-body
                             (let [agg-key (-> opts :agg-key keyword)
                                   agg-top (-> opts :agg-top keyword)
                                   widget-data (-> cursor (get agg-key) :aggregations agg-top)]
                               (om/build detail widget-data)
                               )]]]]]]))
  (did-mount [_]
             (-> (om/get-node owner "wrapper-collapsible")
                 (js/$)
                 (.collapsible (clj->js {:accordion false})))))
