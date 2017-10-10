(ns shove.core
  (:require
   [fn-fx.fx-dom :as dom]
   [fn-fx.diff :refer [component defui render]]
   [fn-fx.controls :as ui]
   [shove.kafkalib :as kafka]
   )
  (:gen-class))




(def firebrick
  (ui/color :red 0.69 :green 0.13 :blue 0.13))


;; The main login window component, notice the authed? parameter, this defines a function
;; we can use to construct these ui components, named "login-form"
(defui LoginWindow
  (render [this {:keys [authed?]}]
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

                 (ui/text-field
                   :id :broker-field
                   :grid-pane/column-index 1
                   :grid-pane/row-index 1)

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
                   :children [(ui/button :text "Submit"
                                :on-action {:event :auth
                                            :fn-fx/include {:broker-field #{:text}
                                                            :content-field #{:text}
                                                            :key-field #{:text}
                                                            :topic-field #{:text}}})]
                   :grid-pane/column-index 1
                   :grid-pane/row-index 5)

                 (ui/text
                   :text (if authed? "Sign in was pressed" "")
                   :fill firebrick
                   :grid-pane/column-index 1
                   :grid-pane/row-index 6)])))

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
                     (println "\n\n\n\n\n\n\n\nUI Event\n\n\n\n\n\n" (str all-data))
                     (case event
                       :auth (let [
                                   
                                   {{ {kf :text} :key-field {bf :text} :broker-field {tf :text} :topic-field {cf :text} :content-field} :fn-fx/includes} all-data
                                   producer (kafka/create-producer bf)
                             ] (do (println "Data" kf bf tf) (try (kafka/send-to-producer producer tf kf cf) (catch Exception e (println "Caught Exception: " (.getMessage e) ) )))
                       (println "Unknown UI event \n\n\n\n\n\n" event all-data))))

(defn -main []
  (let [;; Data State holds the business logic of our app
        data-state (atom {:authed? false})

        ;; handler-fn handles events from the ui and updates the data state

        ;; ui-state holds the most recent state of the ui
        ui-state (agent (dom/app (stage @data-state) handler-fn))]

    ;; Every time the data-state changes, queue up an update of the UI
    (add-watch data-state :ui (fn [_ _ _ _]
                                (send ui-state
                                      (fn [old-ui]
                                        (dom/update-app old-ui (stage @data-state))))))))
