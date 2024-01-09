(ns wgctrl.cluster.balancer
  (:require [clojure.core.async :refer [chan timeout go-loop <! >! >!! <!!]]
    [clojure.core.async.impl.channels :as ch]
    [clojure.core.async.impl.protocols :as p]
    [mount.core :refer [defstate]]
    [wgctrl.cluster.nodes :refer [nodes nodes-active locations]]))


(defn weighted-round-robin
  "A simple weighted round-robin load balancer."
  [nodes]
  (let [buf 1000
        ch (chan buf)]
    (go-loop [i 0]
      (let [node (nth (reverse (sort-by :weight nodes)) i)]
        (dotimes [_ (:weight node)]
          (if (>= (.count (.buf ch)) buf)
            (<! (timeout 100))
            (>! ch node))))
              
      (recur (mod (inc i) (count nodes))))

    ch))

(defn nodes->balancer 
  ([nodes location]
   (let [nodes' (filter #(= location (-> % :location :code)) nodes)]
     (mapv #(zipmap [:uuid :weight :hostname :location] 
              [(:uuid %) (:weight %) (:hostname %) (-> % :location :code )]) nodes')))
  ([nodes]
   (mapv #(zipmap [:uuid :weight :hostname :location] 
            [(:uuid %) (:weight %) (:hostname %) (-> % :location :code )]) nodes)))

(defn balancers! [nodes]
  (let [nodes' (nodes-active nodes)]
    (reduce (fn [acc l] (conj acc {(keyword (:code l)) (weighted-round-robin (nodes->balancer nodes' (:code l)))}))
      {:all (weighted-round-robin (nodes->balancer nodes'))} (-> nodes' locations))))


(defstate balancers :start (balancers! nodes)
  :stop {})





