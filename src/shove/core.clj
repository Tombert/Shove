(ns shove.core
  (:require
   [fn-fx.fx-dom :as dom]
   [fn-fx.diff :refer [component defui render]]
   [fn-fx.controls :as controls]
   [fn-fx.controls :as ui]
   [clojure.data.json :as json]
   [clojure.data.csv :as csv]   
   [fn-fx.util :as util]
   [clojure.string :as str]
   [shove.kafkalib :as kafka]
   [shove.zookeeper :as zookeeper]
   [clojure.java.io :as io]
  )
  (:import (javafx.stage FileChooser)
           (javafx.scene.chart.XYChart)
           (javafx.beans.property ReadOnlyObjectWrapper))
  (:gen-class))

(def state (atom {:zookeepers [] :brokers [] :add-zookeeper false :topics []}))

(def homedir (System/getProperty "user.home"))

(def zookeeperfile (str homedir "/.zookeeperlist.txt"))

(def insets (ui/insets
                 :bottom 25
                 :left 2
                 :right 2
                 :top 2))
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
                     :children [(ui/text-field 
                       :id :new-zookeeper-field
                     
                     ) 
                        (ui/button :text "Done"
                          :on-action {:event :done-add-zookeeper
                                      :fn-fx/include {:new-zookeeper-field #{:text}}})]
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
                                (ui/button :text "Add Zookeeper:"
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
                                :on-action {:event :auth
                                            :fn-fx/include {:zookeeper-field #{:value}
                                                            :content-field #{:text}
                                                            :key-field #{:text}
                                                            :topic-field #{:value}}})]
                   :grid-pane/column-index 1
                   :grid-pane/row-index 5)

                 ])))
;; The main login window component, notice the authed? parameter, this defines a function
;; we can use to construct these ui components, named "login-form"
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
                                                      :zookeeper-field #{:value}
                                                      :fn-fx/event #{:target}
                                                      :topic-field #{:text}
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

(defn csv-data->maps [csv-data]
  (map zipmap
       (->> (first csv-data) ;; First row is the header
            (map keyword) ;; Drop if you want string keys instead
            repeat)
	  (rest csv-data)))

(defn sendMessage [broker topic k value]
  (println "Stuff: " (str broker topic k value))
  (let [
        producer (kafka/create-producer broker)
       ]
    (try (kafka/send-to-producer producer topic k value) (catch Exception e (println "Caught Exception: " (.getMessage e))))
    )
  
  )

(def handlemap {
  :done-add-zookeeper 
      (fn [event all-data state]
                        (let [
                {{{nbf :text} :new-zookeeper-field} :fn-fx/includes} all-data
                {zookeepers :zookeepers} @state

                sortedZookeepers (-> zookeepers
                                 (conj nbf) 
                                 distinct
                                 sort)
                finalZookeepers (filter (fn [x] (not= x "")) sortedZookeepers)
                zookeeperStr (str/join "\n" finalZookeepers)
               ] 
            (swap! state assoc :add-zookeeper false :zookeepers finalZookeepers)
            (spit zookeeperfile zookeeperStr)))
    :add-zookeeper (fn [event all-data state] (swap! state assoc :add-zookeeper true))
 
    :zookeepers-selected 
        (fn [event all-data state] (do (println "\n\n\n\n\n\n\n yo dawg\n\n\n\n\n\n" (str all-data))
                 (let [
                       {includes :fn-fx/includes} all-data
                       {{bf :value} :zookeeper-field} includes
                       zk (zookeeper/createZookeeper bf)
                       brokerids (zookeeper/getValues zk "/brokers/ids")
                       jsonbrokers (map (fn [x] (zookeeper/getData zk (str "/brokers/ids/" x) )) brokerids)
                       lbrokers (mapcat (fn [x] (let [{ep "endpoints"} (json/read-str x)] ep)) jsonbrokers)
                       brokers (mapv (fn [x] (str/replace x #"PLAINTEXT://" "")) lbrokers)

                       ;brokers (doall (fn [x] (zookeeper/getValues (str "/brokers/ids/" x))) brokerids)
                       topics (mapv identity (zookeeper/getValues zk "/brokers/topics"))
                       ] (println "\n\nbrokers: " (str topics)) 
                    (swap! state assoc :brokers brokers :topics topics)
                   )))
    :delete-zookeeper
        (fn [event all-data state]
           (do (println "Howdy" (str all-data))
           (let [
                 
                    {{{nbf :value} :zookeeper-field} :fn-fx/includes} all-data
                    {zookeepers :zookeepers} @state
                    finalZookeepers (filter (fn [x] (println x) (not= x nbf)) zookeepers)
                    zookeeperStr (str/join "\n" finalZookeepers)
                 ]
                (println "Thing" (str nbf))
                (swap! state assoc :zookeepers finalZookeepers)
                (spit zookeeperfile zookeeperStr))))

     :import-csv
        (fn [event all-data state]
           (let [
                 {includes :fn-fx/includes}  all-data
                 {{bf :value } :zookeeper-field {tf :text} :topic-field} includes
                 window (.getWindow (.getScene (:target (:fn-fx/event includes))))
                 dialog (doto (FileChooser.) (.setTitle "Import CSV"))
                 file (util/run-and-wait (.showOpenDialog dialog window))
                 data (with-open [reader (io/reader file)] (doall (csv/read-csv reader)))
                 mdata (csv-data->maps data)
                 jsonstuff (map #(json/write-str %1) mdata )

                 ]
                 (doall (map #(sendMessage bf tf (str (java.util.UUID/randomUUID)) %1) jsonstuff))))

       :auth (fn [event all-data state] (let [
                   ;; Extracts out the fields from the big object that JavaFX gives us
                   ;; TODO: This is uglier than it should be, might break up to multiple lines
                   {{{kf :text} :key-field {bf :value} :zookeeper-field {tf :text} :topic-field {cf :text} :content-field} :fn-fx/includes} all-data

             ] 
               (sendMessage bf tf kf cf) 
               (println "Unknown UI event" event all-data)))
 })

(defn handler-fn [{:keys [event] :as all-data}]
     ((get handlemap event) event all-data state)
     ;(case event
     ;  :done-add-zookeeper
     ;     (let [
     ;           {{{nbf :text} :new-zookeeper-field} :fn-fx/includes} all-data
     ;           {zookeepers :zookeepers} @state

     ;           sortedZookeepers (-> zookeepers
     ;                            (conj nbf) 
     ;                            distinct
     ;                            sort)
     ;           finalZookeepers (filter (fn [x] (not= x "")) sortedZookeepers)
     ;           zookeeperStr (str/join "\n" finalZookeepers)
     ;          ] 
     ;       (swap! state assoc :add-zookeeper false :zookeepers finalZookeepers)
     ;       (spit zookeeperfile zookeeperStr))
     ;  :add-zookeeper (swap! state assoc :add-zookeeper true)
     ;  :delete-zookeeper
     ;      (do (println "Howdy" (str all-data))
     ;      (let [
                 
     ;               {{{nbf :value} :zookeeper-field} :fn-fx/includes} all-data
     ;               {zookeepers :zookeepers} @state
     ;               finalZookeepers (filter (fn [x] (println x) (not= x nbf)) zookeepers)
     ;               zookeeperStr (str/join "\n" finalZookeepers)
     ;            ]
     ;           (println "Thing" (str nbf))
     ;           (swap! state assoc :zookeepers finalZookeepers)
     ;           (spit zookeeperfile zookeeperStr)             
     ;        ))

     ;  :zookeepers-selected (do (println "\n\n\n\n\n\n\n yo dawg\n\n\n\n\n\n" (str all-data))
     ;            (let [
     ;                  {includes :fn-fx/includes} all-data
     ;                  {{bf :value} :zookeeper-field} includes
     ;                  zk (zookeeper/createZookeeper bf)
     ;                  brokerids (zookeeper/getValues zk "/brokers/ids")
     ;                  jsonbrokers (map (fn [x] (zookeeper/getData zk (str "/brokers/ids/" x) )) brokerids)
     ;                  lbrokers (mapcat (fn [x] (let [{ep "endpoints"} (json/read-str x)] ep)) jsonbrokers)
     ;                  brokers (mapv (fn [x] (str/replace x #"PLAINTEXT://" "")) lbrokers)

     ;                  ;brokers (doall (fn [x] (zookeeper/getValues (str "/brokers/ids/" x))) brokerids)
     ;                  topics (mapv identity (zookeeper/getValues zk "/brokers/topics"))
     ;                  ] (println "\n\nbrokers: " (str topics)) 
     ;               (swap! state assoc :brokers brokers :topics topics)
     ;              )
     ;            )
     ;  :import-csv
     ;      (let [
     ;            {includes :fn-fx/includes}  all-data
     ;            {{bf :value } :zookeeper-field {tf :text} :topic-field} includes
     ;            window (.getWindow (.getScene (:target (:fn-fx/event includes))))
     ;            dialog (doto (FileChooser.) (.setTitle "Import CSV"))
     ;            file (util/run-and-wait (.showOpenDialog dialog window))
     ;            data (with-open [reader (io/reader file)] (doall (csv/read-csv reader)))
     ;            mdata (csv-data->maps data)
     ;            jsonstuff (map #(json/write-str %1) mdata )

     ;            ]
     ;            (doall (map #(sendMessage bf tf (str (java.util.UUID/randomUUID)) %1) jsonstuff))

             
             
                 
     ;        ) 
           
     ;  :auth (let [
     ;              ;; Extracts out the fields from the big object that JavaFX gives us
     ;              ;; TODO: This is uglier than it should be, might break up to multiple lines
     ;              {{{kf :text} :key-field {bf :value} :zookeeper-field {tf :text} :topic-field {cf :text} :content-field} :fn-fx/includes} all-data

     ;        ] 
     ;          (sendMessage bf tf kf cf) 
               
               
     ;                  (println "Unknown UI event" event all-data)))
  )

(defn -main []
  (spit zookeeperfile "" :append true)
  (let [;; Data State holds the business logic of our app

        ;; Grab the initial list of zookeepers
        zookeeperlist (->
                       (slurp zookeeperfile)
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
