(ns shove.core
  (:require
   [fn-fx.fx-dom :as dom]
   [fn-fx.diff :refer [component defui render]]
   [fn-fx.controls :as controls]
   [fn-fx.controls :as ui]
   [shove.kafkalib :as kafka]
   )
  (:gen-class))

(def state (atom {:brokers [] :add-broker false}))



;; The main login window component, notice the authed? parameter, this defines a function
;; we can use to construct these ui components, named "login-form"
(defui LoginWindow
  (render [this {:keys [add-broker brokers]}]
    (ui/grid-pane
      :alignment :center
      :hgap 10
      :vgap 10
      :padding (ui/insets
                 :bottom 25
                 :left 25
                 :right 25
                 :top 25)
      :children [(ui/text
                   :text "Shove"
                   :font (ui/font
                           :family "Tahoma"
                           :weight :normal
                           :size 20)
                   :grid-pane/column-index 0
                   :grid-pane/row-index 0
                   :grid-pane/column-span 2
                   :grid-pane/row-span 1)

                 (ui/label
                   :text "Broker:"
                   :grid-pane/column-index 0
                   :grid-pane/row-index 1)

                   
                   (if add-broker 
                   (ui/h-box 
                     :spacing 10
                     :alignment :bottom-left
                     :children [(ui/text-field 
                       :id :new-broker-field
                     
                     ) (ui/button :text "Done"
                                  :on-action {:event :done-add-broker
                                              :fn-fx/include {:new-broker-field #{:text}
}})]
                     :grid-pane/column-index 1
                     :grid-pane/row-index 1
                     
                     )
                   (ui/h-box
                     :spacing 10
                     :alignment :bottom-left
                     :children [
                               (ui/combo-box
                               :id :broker-field
                               :items brokers
                               :grid-pane/column-index 1
                               :grid-pane/row-index 1)
                                (ui/button :text "Add Broker"
                                  :on-action {:event :add-broker
                                              :fn-fx/include {}})]
                     :grid-pane/column-index 1
                     :grid-pane/row-index 1))

                 (ui/label :text "Topic: "
                   :grid-pane/column-index 0
                   :grid-pane/row-index 2)

                 (ui/text-field
                   :id :topic-field
                   :grid-pane/column-index 1
                   :grid-pane/row-index 2)

                 (ui/label :text "Key:"
                   :grid-pane/column-index 0
                   :grid-pane/row-index 3)

                 (ui/text-field
                   :id :key-field
                   :grid-pane/column-index 1
                   :grid-pane/row-index 3)
                 (ui/label :text "Content"
                   :grid-pane/column-index 0
                   :grid-pane/row-index 4)

                 (ui/text-area
                   :id :content-field
                   :grid-pane/column-index 1
                   :grid-pane/row-index 4)
                 (ui/h-box
                   :spacing 10
                   :alignment :bottom-right
                   :children [
                              (controls/button
                                      :text "Import CSV"
                                      :on-action {:event :import-csv
                                                  :fn-fx/include {:fn-fx/event #{:target}}})
                              
                              (controls/button :text "Submit"
                                :on-action {:event :auth
                                            :fn-fx/include {:broker-field #{:value}
                                                            :content-field #{:text}
                                                            :key-field #{:text}
                                                            :topic-field #{:text}}})]
                   :grid-pane/column-index 1
                   :grid-pane/row-index 5)

                 ])))

;; Wrap our login form in a stage/scene, and create a "stage" function
(defui Stage
       (render [this args]
               (ui/stage
                 :title "Shove"
                 :shown true
                 :scene (ui/scene
                          :root (login-window args)))))

;(def b (agent (dom/app  (stage))))
;(send b
;      (fn [old-ui]
;        (dom/update-app old-ui (stage Stage))))


(defn handler-fn [{:keys [event] :as all-data}]
     (println "\n\n\n\n\n\n\n\nUI Event\n\n" (str all-data) "State " (str @state))
     (case event
       :done-add-broker 
          (let [
                {{{nbf :text} :new-broker-field} :fn-fx/includes} all-data
                {brokers :brokers} @state

                ] 
            (println "nbf" nbf)
            (swap! state assoc :add-broker false :brokers (conj brokers nbf)))
       :add-broker (swap! state assoc :add-broker true)
       :auth (let [
                   
                   {{ {kf :text} :key-field {bf :value} :broker-field {tf :text} :topic-field {cf :text} :content-field} :fn-fx/includes} all-data
                   producer (kafka/create-producer bf)
             ] (do (println "Data" kf bf tf) (try (kafka/send-to-producer producer tf kf cf) (catch Exception e (println "Caught Exception: " (.getMessage e) ) )))
                       (println "Unknown UI event \n\n\n\n\n\n" event all-data))))

(defn -main []
  (let [;; Data State holds the business logic of our app

        ;; ui-state holds the most recent state of the ui
        ui-state (agent (dom/app (stage @state) handler-fn))]

    ;; Every time the data-state changes, queue up an update of the UI
    (add-watch state :ui (fn [_ _ _ _]
                                (send ui-state
                                      (fn [old-ui]
                                        (dom/update-app old-ui (stage @state))))))))
