(ns shove.handlers
  (:require
   [clojure.data.json :as json]
   [clojure.data.csv :as csv]   
   [fn-fx.util :as util]
   [clojure.string :as str]
   [clojure.data.csv :as csv]   
   [shove.kafkalib :as kafka]
   [shove.zookeeper :as zookeeper]
   [clojure.java.io :as io]
  )
  (:import (javafx.stage FileChooser)
           (javafx.scene.chart.XYChart)
           (javafx.beans.property ReadOnlyObjectWrapper))
  (:gen-class))

(defn sendMessage 
  "Sends a message to the Kafka broker and topic passed in." 
  [broker topic k value]
  (let [
        producer (kafka/create-producer broker)
       ]
    (try 
      (do 
        (kafka/send-to-producer producer topic k value) 
        (kafka/close-producer producer)) 
      (catch Exception e (println "Caught Exception: " (.getMessage e))))
    )
  
  )

(defn csv-data->maps 
  "A simple helper function to extract the first row of a CSV so that we may get the properties as keywords, which should (theoretically) be faster lookup time."
  [csv-data]
  (map zipmap
       (->> (first csv-data) ;; First row is the header
            (map keyword) ;; Drop if you want string keys instead
            repeat)
	  (rest csv-data)))

(def homedir (System/getProperty "user.home"))

(def zookeeperfile (str homedir "/.zookeeperlist.txt"))

(defn handleDoneAddZookeeper 
      "A handler invoked when the user has entered in the Zookeeper URL, which grabs the data out of the text box, adds it to the list of zookeepers, writes that list to a file for caching, and puts us back into the combobox state."
      [event all-data state] 
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
          (swap! state assoc :add-zookeeper false :zookeepers finalZookeepers :brokers [] :topics [])
          (spit zookeeperfile zookeeperStr)))

(defn handleAddZookeeper 
  "A simple state transformer handler that sets the flag to get us into the add-zookeeper mode, so that we open up a text box and let the user type in a zookeeper" 
  [event all-data state] 
    (swap! state assoc :add-zookeeper true))




(defn handleZookeepersSelected 
    "An event handler that grabs the zookeeper from the selected value of the combobox, and utilizes this to populate the brokers and topics" 
    [event all-data state]
    (do 
       (let [
         {includes :fn-fx/includes} all-data
         {{bf :value} :zookeeper-field} includes
         zk (zookeeper/createZookeeper bf)
         brokerids (zookeeper/getValues zk "/brokers/ids")
         jsonbrokers (map (fn [x] (zookeeper/getData zk (str "/brokers/ids/" x) )) brokerids)
         lbrokers (mapcat (fn [x] (let [{ep "endpoints"} (json/read-str x)] ep)) jsonbrokers)
         brokers (mapv (fn [x] (str/replace x #"PLAINTEXT://" "")) lbrokers)

         topics  (sort (mapv identity (zookeeper/getValues zk "/brokers/topics")))
             ]  
        (swap! state assoc :brokers brokers :topics topics) 
        (zookeeper/closeZookeeper zk)
         )))

(defn handleDeleteZookeeper   
  "An event handler to delete from the state the selected zookeeper.  Also writes the new state to the disk for caching."
  [event all-data state] 

  (do 
     (let [
              {{{nbf :value} :zookeeper-field} :fn-fx/includes} all-data
              {zookeepers :zookeepers} @state
              finalZookeepers (filter (fn [x] (println x) (not= x nbf)) zookeepers)
              zookeeperStr (str/join "\n" finalZookeepers)
           ]
          (swap! state assoc :zookeepers finalZookeepers :topics [] :brokers [])
          (spit zookeeperfile zookeeperStr))))

(defn handleImportCsv 
  "An event handler that will prompt the user for a CSV file, and then send that over to Kafka based on the currently selected broker and topic. Need to change this to be fully triggered by the submit."
  
  [event all-data state]
  (let [
                 {includes :fn-fx/includes}  all-data
                 {{bf :value } :broker-field {tf :value} :topic-field} includes
                 window (.getWindow (.getScene (:target (:fn-fx/event includes))))
                 dialog (doto (FileChooser.) (.setTitle "Import CSV"))
                 file (util/run-and-wait (.showOpenDialog dialog window))
                 data (with-open [reader (io/reader file)] (doall (csv/read-csv reader)))
                 mdata (csv-data->maps data)
                 jsonstuff (map #(json/write-str %1) mdata )

                 ]
                 (doall (map #(sendMessage bf tf (str (java.util.UUID/randomUUID)) %1) jsonstuff))))




(defn handleSubmit 
  "Handler to send the current data in the broker, key, topic, and content fields to kafka."
  [event all-data state]
  (let [
         {{{kf :text} :key-field {bf :value} :broker-field {tf :value} :topic-field {cf :text} :content-field} :fn-fx/includes} all-data
       ] 
       (sendMessage bf tf kf cf)))

(def handlemap {
  :done-add-zookeeper  handleDoneAddZookeeper
  :add-zookeeper handleAddZookeeper
  :zookeepers-selected handleZookeepersSelected
  :delete-zookeeper handleDeleteZookeeper
  :import-csv handleImportCsv
  :submit handleSubmit
 })



