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
   [clojure.java.io :as io]
  )
  (:import (javafx.stage FileChooser)
           (javafx.scene.chart.XYChart)
           (javafx.beans.property ReadOnlyObjectWrapper))
  (:gen-class))

(def state (atom {:brokers [] :add-broker false}))

(def homedir (System/getProperty "user.home"))

(def brokerfile (str homedir "/.brokerlist.txt"))
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
                     
                     ) 
                        (ui/button :text "Done"
                          :on-action {:event :done-add-broker
                                      :fn-fx/include {:new-broker-field #{:text}}})]
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
                        (ui/button :text "Import CSV"
                          :on-action {:event :import-csv
                                      :fn-fx/include {
                                                      :broker-field #{:value}
                                                      :fn-fx/event #{:target}
                                                      :topic-field #{:text}
                                                      }})
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

(defn handler-fn [{:keys [event] :as all-data}]
     (case event
       :done-add-broker 
          (let [
                {{{nbf :text} :new-broker-field} :fn-fx/includes} all-data
                {brokers :brokers} @state

                sortedBrokers (-> brokers 
                                 (conj nbf) 
                                 distinct
                                 sort)
                finalBrokers (filter (fn [x] (not= x "")) sortedBrokers )
                brokerStr (str/join "\n" finalBrokers)
               ] 
            (swap! state assoc :add-broker false :brokers finalBrokers)
            (spit brokerfile brokerStr))
       :add-broker (swap! state assoc :add-broker true)
       :import-csv
           (let [
                 {includes :fn-fx/includes}  all-data
                 {{bf :value } :broker-field {tf :text} :topic-field} includes
                 window (.getWindow (.getScene (:target (:fn-fx/event includes))))
                 dialog (doto (FileChooser.) (.setTitle "Import CSV"))
                 file (util/run-and-wait (.showOpenDialog dialog window))
                 data (with-open [reader (io/reader file)] (doall (csv/read-csv reader)))
                 mdata (csv-data->maps data)
                 jsonstuff (map #(json/write-str %1) mdata )

                 ]
                 (doall (map #(sendMessage bf tf (str (java.util.UUID/randomUUID)) %1) jsonstuff))

             
             
                 
             ) 
           
       :auth (let [
                   ;; Extracts out the fields from the big object that JavaFX gives us
                   ;; TODO: This is uglier than it should be, might break up to multiple lines
                   {{{kf :text} :key-field {bf :value} :broker-field {tf :text} :topic-field {cf :text} :content-field} :fn-fx/includes} all-data

             ] 
               (sendMessage bf tf kf cf) 
               
               
                       (println "Unknown UI event" event all-data))))

(defn -main []
  (spit brokerfile "" :append true)
  (let [;; Data State holds the business logic of our app

        ;; Grab the initial list of brokers
        brokerlist (->
                       (slurp brokerfile)
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
    (swap! state assoc :brokers brokerlist)
    ))
