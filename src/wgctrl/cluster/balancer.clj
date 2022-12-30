(ns wgctrl.cluster.balancer
  (:require [clojure.core.async :refer [chan dropping-buffer timeout go-loop put! <!]]
            [clojure.core.async.impl.channels :as ch]
            [clojure.core.async.impl.protocols :as p]
            [wgctrl.cluster.selectors :as s]
            [wgctrl.cluster.transforms :as t]
           ))


(defn weighted-round-robin
  "A simple weighted round-robin load balancer."
  [nodes]
  (let [in (chan (dropping-buffer 1000))
        out (chan (dropping-buffer 1000))]
    (go-loop [i 0]
        (when-let [node (nth nodes i)] 
          (if (>= (.count (.buf out)) 100)
            (<! (timeout 1000))
            (dotimes [_ (:weight node)]
              (put! in node)))
            (recur (mod (inc i) (count nodes)))))
    
    (go-loop [node (<! in)]
        (put! out node)
        (if (>= (.count (.buf in)) 100)
                (<! (timeout 1000))
                (recur (<! in))))
     out))


(defn balancers! [c]
  (let [nodes @(.nodes c)]
    (t/balancer->cluster {:all (weighted-round-robin (vec nodes))} c)
    (for [loc (s/available-locations nodes)]
      (t/balancer->cluster {(keyword (:code loc)) (weighted-round-robin 
                          (vec (s/uuid-nodes-by-location
                             nodes (:code loc))))} c))))
