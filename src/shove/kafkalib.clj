(ns shove.kafkalib
  (:import (kafka.consumer Consumer ConsumerConfig KafkaStream)
           (org.apache.kafka.clients.consumer KafkaConsumer)
           (kafka.producer KeyedMessage ProducerConfig)
           (kafka.javaapi.producer Producer)
           (java.util Properties)
           (java.util.concurrent Executors))
  (:gen-class))


(defn create-producer
  "Creates a producer that can be used to send a message to Kafka"
  [brokers]
  (let [props (Properties.)]
    (doto props
      (.put "metadata.broker.list" brokers)
      (.put "serializer.class" "kafka.serializer.StringEncoder")
      (.put "request.required.acks" "1"))
    (Producer. (ProducerConfig. props))))

(defn send-to-producer
  "Send a string message to Kafka"
  [producer topic kkey message]
  (let [data (KeyedMessage. topic kkey message)]
    (.send producer data)))
(defn close-producer [producer] (.close producer))



(defrecord KafkaMessage [topic offset partition key value-bytes])

(defn- create-consumer-config
  "Returns a configuration for a Kafka client."
  [zk]
  (let [props (Properties.)]
    (println "\n\n\n\n\n\nmade it to consumer-config")
    (doto props
      (.put "zookeeper.connect" zk)
      (.put "group.id" (str (java.util.UUID/randomUUID)))
      (.put "zookeeper.session.timeout.ms" "400")
      (.put "zookeeper.sync.time.ms" "200")
      (.put "auto.commit.interval.ms" "1000"))

    (let [c (ConsumerConfig. props)] (println "\n\n\n\n\n\nmade it to consumer-config2") c)
    ))

(defn consume-messages
  "Continually consume messages from a Kafka topic and write message value to stdout."
  [stream thread-num]
  (let [it (.iterator ^KafkaStream stream)]
    (println (str "\n\n\n\n\nStarting thread " thread-num))
    (while (.hasNext it)
      (as-> (.next it) msg
            (KafkaMessage. (.topic msg) (.offset msg) (.partition msg) (.key msg) (.message msg))
            (println (str "\n\n\n\n\n\nReceived on thread " thread-num ": " (String. (:value-bytes msg))))))
    (println (str "\n\n\n\n\n\n\nStopping thread " thread-num))))

(defn- start-consumer-threads
  "Start a thread for each stream."
  [thread-pool kafka-streams]
  (loop [streams kafka-streams
         index 0]
    (when (seq streams)
      (.submit thread-pool (cast Callable #(consume-messages (first streams) index)))
      (recur (rest streams) (inc index)))))



; (defn getMessages [zk topic]
;   (let 
;     [
;      consumer (Consumer/createJavaConsumerConnector (create-consumer-config zk))
;      consumer-map (.createMessageStreams consumer {topic 1})
;      ; kafka-streams (.get consumer-map topic)
;      ; thread-pool (Executors/newFixedThreadPool 1)
;     ]
;     (println "\n\n\n\n Made it to getmessages")
;     ; (.addShutdownHook (Runtime/getRuntime)
;     ;   (Thread. #(do (.shutdown consumer)
;     ;                 (.shutdown thread-pool))))
;       ; (start-consumer-threads thread-pool kafka-streams)
;     ))


; Properties props = new Properties();
; props.put("bootstrap.servers", "localhost:9092");
; props.put("group.id", "consumer-tutorial");
; props.put("key.deserializer", StringDeserializer.class.getName());
; props.put("value.deserializer", StringDeserializer.class.getName());
; KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props); 

(defn getMessages [broker topic]
   (let [p (Properties.)]
    (println "\n\n\n\n Getting messages\n\n\n" (str (type  p)))
    (.put p "bootstrap.servers" broker) 
    (.put p "group.id" "test")
    (.put p "key.deserializer" "org.apache.kafka.common.serialization.StringDeserializer")
    (.put p "value.deserializer" "org.apache.kafka.common.serialization.StringDeserializer")
    (println (str "\n\n\nOutside second let." broker topic))
    (let [consumer (KafkaConsumer. p)
          ll (java.util.Arrays/asList (into-array [topic]))]
        (try 
      (loop [count 0]
        (println (str "yo" ll))
        (.subscribe consumer ll)
        (let [records (.poll consumer 10000)]
            (println (str "\n\n\n\n\n\n\n\n\n\n\n\nRecord: \n\n\n\n\n\n\n\n\n" (.count records)))
           (doseq [rec records]
              (println (str "Square of " (.value rec))))
            ;(map (fn [x] (println (str "Real record" (.value x)))) (seq records))
            )
        (if (< count 100) (recur (+ 0 count)))
        )
       (catch Exception e (println (str "Exception yo: " e)))
      ))))


