(ns wgctrl.cluster.balancer
  (:require [clojure.core.async :refer [chan timeout go-loop <! >! >!! <!!]]
    [clojure.core.async.impl.channels :as ch]
    [clojure.core.async.impl.protocols :as p]
    [wgctrl.cluster.selectors :as s]
    [wgctrl.cluster.transforms :as t]))


(defn weighted-round-robin
  ; todo - sort nodes by weight
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


(defn next-node [b]
  (<!! b))

(defn balancer! [c]
  (let [nodes (map (fn [x] 
                     {:uuid (-> x .uuid ) 
                      :hostname (-> x .hostname ) 
                      :weight (-> x .weight )
                      :location (-> x .location :code )})  @(.nodes c))]
    (t/balancer->cluster {:all (weighted-round-robin (vec nodes))} c)

    (for [loc (s/available-locations nodes)]
      (t/balancer->cluster {(keyword loc) (weighted-round-robin 
                                            (s/uuid-nodes-by-location nodes loc))} c))))


