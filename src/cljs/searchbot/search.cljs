(ns searchbot.search
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<! >! chan]]
            [searchbot.meta :refer [widget-wrapper detail]]
            [clojure.string :as string]))

;;;;;;;;;;;;;
;; searchbox

(defn es-query [api-url post-body]
  (let [c (chan)]
    (go (let [{resp :body} (<! (http/post api-url {:json-params (-> post-body)}))]
          (>! c resp)))
    c))

(defn- update-searchbox
  [owner inputValue]
  (let [newQuery (if (> (count inputValue) 0)
                   {:query {:query_string {:query inputValue}}}
                   {:query {:match_all {}}})]
    (om/set-state! owner :query newQuery)
    (om/set-state! owner :query-string inputValue)))

(defn- update-query-result
  [owner result]
  (om/set-state! owner :query-result result))

(defn- join-str [& tokens]
  (->> tokens (filter (comp not string/blank?)) (string/join "/") (str "/")))

(defcomponent searchbox [cursor owner opts]
  (will-mount [_]
              (update-searchbox owner "")
              (update-query-result owner {}))
  (render-state [_ {:keys [query query-string query-result]}]
                (let [reference "query-string"]
                  (html [:.card
                         [:.card-content
                          [:span.card-title.black-text "searching in "
                           [:strong (join-str (:es-index opts) (:es-type opts))]
                           [:a.btn-floating.btn-flat.waves-effect.activator.white.right
                            [:i.mdi-action-settings.grey-text]]]
                          [:.input-field
                           [:i.prefix.fa.fa-search]
                           [:input {:id (:id opts)
                                    :ref (:id opts)
                                    :value query-string
                                    :type "text"
                                    :on-change (fn [x]
                                                 (let [thisNode (om/get-node owner (:id opts))
                                                       inputValue (.-value thisNode)]
                                                   (update-searchbox owner inputValue)))
                                    :on-key-down (fn [e]
                                                   (let [keyCode (-> e .-keyCode)]
                                                     (case keyCode
                                                       27 (do ;; ESC
                                                            (update-searchbox owner "")
                                                            (update-query-result owner {}))
                                                       13 (go ;; ENTER
                                                           (let [resp (<! (es-query
                                                                           (join-str "es" (:es-index opts) (:es-type opts) "_search")
                                                                           query))]
                                                             (update-query-result owner resp)))
                                                       nil)))}]
                           [:label {:for (:id opts)} "query string"]]
                          [:div "query"
                           [:pre {:style {:font-size "8pt"}}
                            [:code.language-javascript {:ref "current-query-string"} (.stringify js/JSON (clj->js query) nil 4)]]]
                          [:div (str "resp: " (or (count (get-in query-result [:hits :hits])) 0) "/"
                                     (or (get-in query-result [:hits :total]) 0) " hits")
                           [:table.responsive-table.hoverable.bordered
                            [:thead
                             [:tr
                              (for [col (:header opts)]
                                [:th (:label col) (str " (" (string/join "/" (:field col))")")])]]
                            [:tbody
                             (let [hits (get-in query-result [:hits :hits])]
                               (for [hit hits]
                                 [:tr
                                  (for [col (:header opts)]
                                    [:td (get-in hit (map keyword (:field col)))])]))]]]]
                         [:.card-reveal
                          [:span.card-title.grey-text.text-darken-4 "metadata"
                           [:i.mdi-navigation-close.right]
                           [:ul.collapsible {:ref "wrapper-collapsible" :data-collapsible "accordion"}
                            [:li
                             [:div.collapsible-header [:i.fa.fa-trello] "Spec"]
                             [:div.collapsible-body (om/build detail opts)]]
                            [:li
                             [:div.collapsible-header [:i.fa.fa-list-ol] "Response"]
                             [:div.collapsible-body (om/build detail query-result)]]]]]
                         ])))
  (did-update [_ _ _]
              (let [input (om/get-node owner "current-query-string")]
                (.highlightElement js/Prism input)))
  (did-mount [_]
             (-> (om/get-node owner "wrapper-collapsible")
                 (js/$)
                 (.collapsible (clj->js {:accordion false})))))
