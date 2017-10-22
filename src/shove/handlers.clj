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

(defn sendMessage [broker topic k value]
  (println "Stuff: " (str broker topic k value))
  (let [
        producer (kafka/create-producer broker)
       ]
    (try (kafka/send-to-producer producer topic k value) (catch Exception e (println "Caught Exception: " (.getMessage e))))
    )
  
  )

(defn csv-data->maps [csv-data]
  (map zipmap
       (->> (first csv-data) ;; First row is the header
            (map keyword) ;; Drop if you want string keys instead
            repeat)
	  (rest csv-data)))

(def homedir (System/getProperty "user.home"))

(def zookeeperfile (str homedir "/.zookeeperlist.txt"))

(defn handleDoneAddZookeeper [event all-data state] 
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

(defn handleAddZookeeper [event all-data state] 
  (swap! state assoc :add-zookeeper true))

(defn handleZookeepersSelected [event all-data state]
  (do (println "\n\n\n\n\n\n\n yo dawg\n\n\n\n\n\n" (str all-data))
                 (let [
                       {includes :fn-fx/includes} all-data
                       {{bf :value} :zookeeper-field} includes
                       zk (zookeeper/createZookeeper bf)
                       brokerids (zookeeper/getValues zk "/brokers/ids")
                       jsonbrokers (map (fn [x] (zookeeper/getData zk (str "/brokers/ids/" x) )) brokerids)
                       lbrokers (mapcat (fn [x] (let [{ep "endpoints"} (json/read-str x)] ep)) jsonbrokers)
                       brokers (mapv (fn [x] (str/replace x #"PLAINTEXT://" "")) lbrokers)

                       topics (mapv identity (zookeeper/getValues zk "/brokers/topics"))
                       ] (println "\n\nbrokers: " (str topics)) 
                    (swap! state assoc :brokers brokers :topics topics))))

(defn handleDeleteZookeeper [event all-data state] 
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


(defn handleImportCsv [event all-data state]
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




(defn handleSubmit [event all-data state]
  (let [
         {{{kf :text} :key-field {bf :value} :zookeeper-field {tf :text} :topic-field {cf :text} :content-field} :fn-fx/includes} all-data
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



