(ns shove.core
  (:require
   [fn-fx.fx-dom :as dom]
   [fn-fx.diff :refer [component defui render]]
   [fn-fx.controls :as controls]
   [fn-fx.controls :as ui]
   [clojure.string :as str]
   [shove.handlers :as handlers]
  )
  
  (:gen-class))

(def state (atom {:zookeepers [] :brokers [] :add-zookeeper false :topics []}))


(def doneAddZookeeperButton 
  (ui/button :text "Done"
    :on-action 
      {:event :done-add-zookeeper
       :fn-fx/include {:new-zookeeper-field #{:text}}}))
(def insets (ui/insets
                 :bottom 25
                 :left 2
                 :right 2
                 :top 2))
(def newZookeperTextField (ui/text-field 
                       :id :new-zookeeper-field
                     
                     ))


(defn shoveContentTab [zookeepers topics brokers add-zookeeper] (ui/tab 
                    :text "Shove Content"
                    :closable false
                    :content
                        (ui/grid-pane

                        :hgap 10
                        :vgap 10
                          :padding insets
                          :children [
                        (ui/label
                    :text "Zookeeper:"
                    :grid-pane/column-index 0
                    :grid-pane/row-index 1)

                   
                   (if add-zookeeper
                   (ui/h-box 
                     :spacing 10
                     :alignment :bottom-left
                     :children [ 
                     newZookeperTextField
                     doneAddZookeeperButton
                        ]
                     :grid-pane/column-index 1
                     :grid-pane/row-index 1
                     
                     )
                   (ui/h-box
                     :spacing 10
                     :alignment :bottom-left
                     :children [

                               (ui/combo-box
                               :id :zookeeper-field
                               :items zookeepers
                               :grid-pane/column-index 1
                               :on-action {:event :zookeepers-selected :fn-fx/include {:zookeeper-field #{:value}}}
                               :grid-pane/row-index 1)
                                (ui/button :text "Add Zookeeper"
                                  :on-action {:event :add-zookeeper
                                              :fn-fx/include {}})
                                
                                (ui/button :text "Delete Zookeeper"
                                  :on-action {:event :delete-zookeeper
                                              :fn-fx/include 
                                                 {:zookeeper-field #{:value}}})
                                ]
                     :grid-pane/column-index 1
                     :grid-pane/row-index 1)
                     )
                   
                 (ui/label :text "Broker: "
                   :grid-pane/column-index 0
                   :grid-pane/row-index 2)

                 (ui/combo-box 
                   :id :broker-field
                   :items brokers
                   :grid-pane/column-index 1
                   :grid-pane/row-index 2)

                 (ui/label :text "Topic: "
                   :grid-pane/column-index 0
                   :grid-pane/row-index 3)

                 (ui/combo-box
                   :id :topic-field
                   :items topics
                   :grid-pane/column-index 1
                   :grid-pane/row-index 3)

                 (ui/label :text "Key:"
                   :grid-pane/column-index 0
                   :grid-pane/row-index 4)

                 (ui/text-field
                   :id :key-field
                   :grid-pane/column-index 1
                   :grid-pane/row-index 4)
                 (ui/label :text "Content"
                   :grid-pane/column-index 0
                   :grid-pane/row-index 5)

                 (ui/text-area
                   :id :content-field
                   :grid-pane/column-index 1
                   :grid-pane/row-index 5)
                 (ui/h-box
                   :spacing 10
                   :alignment :bottom-right
                   :children [
                              (controls/button :text "Submit"
                                :on-action {:event :submit
                                            :fn-fx/include {:zookeeper-field #{:value}
                                                            :broker-field #{:value}
                                                            :content-field #{:text}
                                                            :key-field #{:text}
                                                            :topic-field #{:value}}})]
                   :grid-pane/column-index 1
                   :grid-pane/row-index 5)])))

(defui LoginWindow
  (render [this {:keys [add-zookeeper topics brokers zookeepers]}]
    (ui/tab-pane
      :padding insets
      :tabs [   (shoveContentTab zookeepers topics brokers add-zookeeper)
                 (ui/tab 
                   :text "CSV"
                   :closable false
                   :content 
                      (ui/grid-pane
                        :hgap 10
                        :vgap 10
                          :padding (ui/insets
                         :bottom 25
                         :left 2
                         :right 2
                         :top 25)
                         :children [
                                     
                        (ui/button :text "Import CSV"
                          :on-action {:event :import-csv
                                      :fn-fx/include {
                                                      :broker-field #{:value}
                                                      :fn-fx/event #{:target}
                                                      :topic-field #{:value}
                                     }})
                                    ]
                   
                   ))



])))

;; Wrap our login form in a stage/scene, and create a "stage" function
(defui Stage
       (render [this args]
               (ui/stage
                 :title "Shove"
                 :shown true
                 :scene (ui/scene
                          :root (login-window args)))))




(defn handler-fn [{:keys [event] :as all-data}]
     ((get handlers/handlemap event) event all-data state))

(defn -main []
  (spit handlers/zookeeperfile "" :append true)
  (let [;; Data State holds the business logic of our app

        ;; Grab the initial list of zookeepers
        zookeeperlist (->
                       (slurp handlers/zookeeperfile)
                       (str/split #"\n")
                   )

        ;; ui-state holds the most recent state of the ui
        ui-state (agent (dom/app (stage @state) handler-fn))]
    

    ;; Every time the data-state changes, queue up an update of the UI
    (add-watch state :ui 
        (fn [_ _ _ _]
            (send ui-state
                (fn [old-ui]
                  (dom/update-app old-ui (stage @state))))))

    ;; Load up the state with the inital list of brokers
    (swap! state assoc :zookeepers zookeeperlist)
    ))
