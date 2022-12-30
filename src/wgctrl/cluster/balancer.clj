(ns wgctrl.cluster.balancer
  (:require [clojure.core.async :refer [chan dropping-buffer timeout buffer go go-loop put! <! >! >!! <!!]]
            [clojure.core.async.impl.channels :as ch]
            [clojure.core.async.impl.protocols :as p]
            [wgctrl.cluster.selectors :as s]
            [wgctrl.cluster.transforms :as t]
           ))


(defn weighted-round-robin
  "A simple weighted round-robin load balancer."
  [nodes]
  (let [in (chan 100)
        out (chan 100)]
    (go-loop [i 0]
        (when-let [node (nth nodes i)] 
       ;   (println "OUT Buffer" node " -- " (.count (.buf out)))
          (if (>= (.count (.buf out)) 100)
            (<! (timeout 1000))
            (dotimes [_ (:weight node)]
              (put! in node)))
            (recur (mod (inc i) (count nodes)))))
    
    (go-loop [node (<! in)]
       ; (println "IN Buffer" node " -- " (.count (.buf in)))
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



(def test1 [{:weight 1 :name "a"}
{:weight 1 :name "b"}
{:weight 1 :name "c"}
{:weight 1 :name "d"}
{:weight 1 :name "e"}
{:weight 1 :name "f"}
{:weight 1 :name "g"}
{:weight 1 :name "h"}
{:weight 1 :name "i"}])

(def test2 [{:weight 1 :name "a"}
{:weight 2 :name "b"}
{:weight 3 :name "c"}
{:weight 4 :name "d"}
{:weight 5 :name "e"}
{:weight 6 :name "f"}
{:weight 7 :name "g"}
{:weight 8 :name "h"}
{:weight 9 :name "g"}])

(def test3 [{:weight 1 :name "a"}
{:weight 1 :name "b"}
{:weight 1 :name "c"}])

(def test4 [{:weight 1 :name "a"}
{:weight 2 :name "b"}])
