(ns shove.zookeeper
  (:import 
  (org.apache.zookeeper CreateMode)
  (org.apache.zookeeper WatchedEvent)
  (org.apache.zookeeper Watcher)
  ;(org.apache.zookeeper.Watcher.Event KeeperState)
  ;(org.apache.zookeeper ZooDefs.Ids)
  (org.apache.zookeeper ZooKeeper))
  (:gen-class))

(defn createZookeeper [^String url] 
  (let
      [
       watcher (reify Watcher (^void process [this ^WatchedEvent e] nil))
       zk (new ZooKeeper url 3000 watcher)
       
      ] zk))

(defn getValues [zk path]
  (.getChildren zk path false))

(defn closeZookeeper [zk] (.close zk))
(defn getData [zk path]
  (new String (.getData zk path false nil))
  )

