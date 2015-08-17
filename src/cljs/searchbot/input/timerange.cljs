(ns searchbot.input.timerange
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]))

(defcomponent timerange [app owner opts]
  (init-state [_]
              {:enabled true
               :active "1h"
               :base-date (js/Date.)
               :predefined [{:name "14 days" :val "14d"}
                            {:name "7 days" :val "7d"}
                            {:name "2 days" :val "2d"}
                            {:name "1 days" :val "1d"}
                            {:name "12 hrs" :val "12h"}
                            {:name "4 hrs" :val "4h"}
                            {:name "2 hrs" :val "2h"}
                            {:name "1 hr" :val "1h"}]})
  (render-state [_ {:keys [active base-date predefined]}]
                (html
                 [:.row
                  [:.col
                   [:.card
                    [:.card-content
                     [:.row [:label "Last"]]
                     (for [p (partition 4 predefined)]
                       [:.row
                        [:ul.pagination
                         (for [v p]
                           [:li.waves-effect
                            {:class (if (= active (:val v)) "active")
                             :on-click (fn [_]
                                         (om/set-state! owner :active (:val v))
                                         )}
                            [:a {:href "#!"}
                             (:val v)]])
                         ]])
                     [:.row
                      [:label "of"]
                      [:.col.offset-s1
                       [:input.datepicker
                        {:ref "base-date" :type "date"
                         :placeholder (.toDateString base-date)}]
                       ]]]]]]))
  (did-mount [_]
             (-> (om/get-node owner "base-date")
                 js/$
                 (.pickadate (clj->js {:format "yyyy-mm-dd" :max true :min -14
                                       :onSet (fn [context]
                                                (if (:select context)
                                                  (om/set-state! owner :base-date (js/Date. (:select context))))
                                                )})))
             )
  )
