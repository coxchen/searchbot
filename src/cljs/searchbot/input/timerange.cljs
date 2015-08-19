(ns searchbot.input.timerange
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]))

(defn- mt->display [the-moment]
  (.format the-moment "YYYY-MM-DD"))

(defn- mt->query [the-moment]
  (.format the-moment "YYYY-MM-DDTHH:mm:ssZ"))

(defn- ->moment
  ([] (js/moment))
  ([timestamp] (js/moment timestamp)))

(defn- gen-query!
  [cursor]
  (let [base-date (:base-date cursor)
        active (:active cursor)
        lte (-> base-date ->moment (.add 1 "d"))
        gte (-> lte ->moment (.subtract (:val active) (:unit active)))
        query {:range {:_timestamp {:gte (mt->query gte) :lte (mt->query lte)}}}]
    (om/update! cursor :generated-query query)))

(defcomponent timerange [cursor owner opts]
  (render-state [_ _]
                (html
                 [:.row
                  [:.col
                   [:.card
                    [:.card-content
                     [:.col
                      [:.row [:label "Last"]]
                      (for [p (partition 8 (:predefined cursor))]
                        [:.row
                         [:ul.pagination
                          (for [v p]
                            [:li.waves-effect
                             {:class (if (= (:active cursor) v) "active")
                              :on-click (fn [_]
                                          (om/update! cursor :active v))}
                             [:a {:href "#!"} (:name v)]])]])]
                     [:.col
                      [:.row
                       [:label "of"]
                       [:.col.offset-s1
                        [:input.datepicker {:ref "base-date" :type "date"
                                            :placeholder (-> cursor :base-date ->moment mt->display)}]
                        ]]]]]]]))
  (did-update [_ _ _]
              (.log js/console (-> cursor ((juxt :active :base-date)) pr-str))
              (gen-query! cursor))
  (did-mount [_]
             (let [base-date (-> cursor :base-date ->moment)
                   min-date-val (-> opts :min js/Math.abs)
                   min-date (.subtract (->moment base-date) min-date-val "d")]
               (om/update! cursor :base-date base-date)
               (gen-query! cursor)
               (-> (om/get-node owner "base-date")
                   js/$
                   (.pickadate (clj->js
                                {:format (:format opts) :max (:base-date cursor) :min (.format min-date)
                                 :onSet (fn [context]
                                          (if-let [selected (-> context (.. -select))]
                                            (do
                                              (om/update! cursor :base-date (->moment selected))))
                                          )})))
               ))
  )
