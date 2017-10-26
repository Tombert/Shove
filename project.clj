(defproject shove "0.1.4-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [halgari/fn-fx "0.4.0"]
                 [org.apache.zookeeper/zookeeper "3.5.3-beta"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/data.csv "0.1.4"]
                 [org.apache.kafka/kafka-clients "0.9.0.0"]
                 [org.apache.kafka/kafka_2.9.2 "0.8.1.1" :exclusions [javax.jms/jms
                                                                      com.sun.jdmk/jmxtools
                                                                      com.sun.jmx/jmxri]]
                 ]
  :main shove.core
  :aot [shove.core]
  :target-path "target/%s"
 ; :profiles {:uberjar {:aot :all}}
  
  
  )
