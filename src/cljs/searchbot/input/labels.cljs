(ns searchbot.input.labels
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]))

(defn labelize
  [input-string]
  (-> input-string
      (clojure.string/split #" ")
      ((partial map (fn [l] {:name l})))))

(defn- get-input-value [owner target] (.-value (om/get-node owner target)))

(defn- checked? [owner target] (.-checked (om/get-node owner target)))

(defn- toggle-edit! [owner target] (om/set-state! owner :editing-labels? (not (checked? owner target))))

(defn- update-labels! [owner input-value]
  (let [new-labels (labelize input-value)]
    (om/set-state! owner :labels new-labels)
    (om/set-state! owner :labels-string input-value)))

(defn- style-trans
  [trans-str]
  {:transition trans-str
   :-moz-transition trans-str
   :-webkit-transition trans-str
   :-o-transition trans-str})

(defn- style-height-expand
  [height duration]
  (merge {:height (str height "px") :overflow "hidden"}
         (style-trans (str "height " duration))))

(defn- make-labels-input
  [{:keys [owner height labels-string on-changed! on-updated!] :or {height 0}}]
  (html
   [:.input-field {:style (style-height-expand height "0.5s")}
    [:i.prefix.fa.fa-tag]
    [:input {:ref "labels-input" :type "text" :value labels-string
             :on-change (fn [x]
                          (let [inputValue (get-input-value owner "labels-input")]
                            (on-changed! owner inputValue)))
             :on-key-down (fn [e]
                            (let [keyCode (-> e .-keyCode)]
                              (case keyCode
                                13 (go ;; ENTER
                                    (do
                                      (on-updated! (get-input-value owner "labels-input"))
                                      (-> (om/get-node owner "labels-input")
                                          js/$
                                          (.blur))
                                      (toggle-edit! owner "toggle-edit")))
                                nil)))

             }]
    ]))

(defcomponent labels [app owner {:keys [labels on-label-updated!]}]
  (init-state [_]
              {:labels labels
               :labels-string (clojure.string/join " " (map :name labels))
               :editing-labels? false})
  (render-state [_ {:keys [labels labels-string editing-labels?]}]
                (html
                 [:.card
                  [:.card-content {:ref "card"}
                   [:.switch.right {:style {:display "inline" :margin-top "10px" :margin-left "10px"}}
                    [:label
                     [(if editing-labels? :span :strong) "Done"]
                     [:input {:type "checkbox" :ref "toggle-edit"
                              :checked editing-labels?
                              :on-change (fn [x]
                                           (let [thisNode (om/get-node owner "toggle-edit")
                                                 checked (.-checked thisNode)]
                                             (om/set-state! owner :editing-labels? checked)
                                             (if (not checked) ; execute on closing
                                               (on-label-updated! (get-input-value owner "labels-input")))
                                             ))}]
                     [:span.lever]
                     [(if editing-labels? :strong :span) "Edit"]]]
                   (for [l labels]
                     [:a.teal.btn {:style {:cursor "default" :text-transform "none" :margin-right "5px" :margin-top "5px"}}
                      [:i.fa.fa-tag.left] (:name l)])
                   (make-labels-input {:owner owner :labels-string labels-string
                                       :height (if editing-labels? 50 0)
                                       :on-changed! update-labels!
                                       :on-updated! on-label-updated!})

                   ]]))
  )
