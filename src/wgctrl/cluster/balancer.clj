(ns wgctrl.cluster.balancer
  (:require [clojure.core.async :refer [chan go go-loop put! <! <!!]]
            [clojure.core.async.impl.channels :as ch]
            [clojure.core.async.impl.protocols :as p]
            [wgctrl.cluster.selectors :as s]
            [wgctrl.cluster.state :as state]))

(defn random [nodes]
  (rand-nth nodes))

(defn weighted-round-robin
  "A simple weighted round-robin load balancer."
  [nodes]
  (let [in (chan)
        out (chan)]
    (go-loop [i 0]
      (when-let [node (nth nodes i)]
        (dotimes [_ (:weight node)]
          (put! in node))
        (recur (mod (inc i) (count nodes)))))
    (go-loop [node (<! in)]
      (put! out node)
      (recur (<! in)))
    out))

(def balancer
  (reduce (fn [acc loc]
            (conj acc {(keyword (:code loc))
                       (weighted-round-robin
                        (vec (s/uuid-nodes-by-location
                              @(.nodes state/cluster)
                              (:code loc))))}))
          {} (s/available-locations @(.nodes state/cluster))))

;(-> balancer)

;(<!! (:dev balancer))
;(<!! (:lt balancer))
;