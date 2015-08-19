(ns searchbot.filters
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [searchbot.input.timerange :refer [timerange]]))


(defn- get-filter [filter-type]
  (case filter-type
    "timerange" timerange
    nil))

(defn- make-it-collapsible [owner ref-node]
  (let [collapsible (om/get-node owner ref-node)]
    (-> collapsible js/$ (.collapsible {:accordion false}))))

(defn- combine-filters! [cursor]
  (let [generated (mapv #(-> % second :generated-query) @cursor)
        combined {:filter {:bool {:must generated}}}]
    (om/update! cursor :combined-query combined)
    (.log js/console "@ combined" (-> @cursor :combined-query pr-str))))

(defcomponent filters [cursor owner opts]
  (render [_]
          (html
           [:.row
            [:.col.s12
             [:ul.collapsible.blue-grey.lighten-2.amber-text.text-accent-4
              {:ref "filters-collapsible" :data-collapsible "accordion"}
              [:li
               [:.collapsible-header [:i.fa.fa-filter] "filters"]
               [:.collapsible-body
                [:.row
                 (for [f (vec (:filters opts))]
                   (if-let [a-filter (get-filter (:type f))]
                     [:.col (om/build a-filter (get cursor (-> f :cursor keyword)) {:opts f})]))
                ]]
               ]]]]))
  (did-update [_ _ _]
              (.log js/console "@ filters got updated")
              (combine-filters! cursor))
  (did-mount [_]
             (combine-filters! cursor)
             (make-it-collapsible owner "filters-collapsible"))
  )

