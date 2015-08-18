(ns searchbot.input.timerange
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]))

(defn format-moment [the-moment]
  (.format the-moment "YYYY-MM-DD"))

(defn js-date
  ([] (js/moment))
  ([timestamp] (js/moment timestamp)))

(defcomponent timerange [cursor owner opts]
  (render-state [_ _]
                (html
                 [:.row
                  [:.col
                   [:.card
                    [:.card-content
                     [:.row [:label "Last"]]
                     (for [p (partition 4 (:predefined cursor))]
                       [:.row
                        [:ul.pagination
                         (for [v p]
                           [:li.waves-effect
                            {:class (if (= (:active cursor) (:name v)) "active")
                             :on-click (fn [_]
                                         (om/update! cursor :active (:name v)))}
                            [:a {:href "#!"}
                             (:name v)]])]])
                     [:.row
                      [:label "of"]
                      [:.col.offset-s1
                       [:input.datepicker {:ref "base-date" :type "date"
                                           :placeholder (-> cursor :base-date js-date format-moment)}]
                       ]]]]]]))
  (did-update [_ _ _]
              (.log js/console (-> cursor ((juxt :active :base-date)) pr-str)))
  (did-mount [_]
             (let [base-date (-> cursor :base-date js-date)
                   min-date-val (-> opts :min js/Math.abs)
                   min-date (.subtract (js-date base-date) min-date-val "d")]
               (om/update! cursor :base-date base-date)
               (-> (om/get-node owner "base-date")
                   js/$
                   (.pickadate (clj->js
                                {:format (:format opts) :max (:base-date cursor) :min (.format min-date)
                                 :onSet (fn [context]
                                          (if-let [selected (-> context (.. -select))]
                                            (do
                                              (om/update! cursor :base-date (js-date selected))))
                                          )})))
               ))
  )
