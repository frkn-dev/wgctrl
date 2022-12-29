(ns wgctrl.cluster.state
  (:require [wgctrl.cluster.ssh :as ssh]
            [wgctrl.cluster.transforms :as t]
            [wgctrl.cluster.model :as m]))

(defonce cluster (m/cluster!))

(defn restore-state
  "Restores state of WG Cluster, adds Nodes with Interfaces according config,
   adds Peers by ssh'ing each node.

   For big cluster could take time! SSH is slow"
  [config]
  ; Checking and adding nodes
  (reduce (fn [cluster' data']
            (t/node->cluster (m/node! (ssh/node-reg-data data')) cluster'))
          cluster (:nodes config))

  ; Checking and adding peers 
  (loop [nodes @(.nodes cluster)]
    (loop [interfaces @(.interfaces (first nodes))]
      (if (empty? interfaces)
        nil
        (let [i (first interfaces)
              peers (ssh/peers (str "root@" (-> i :endpoint :inet))
                               (-> i :name))]
          (reduce (fn [interface peer]
                    (t/peer->interface (m/peer! (vals peer)) interface)) i peers)
          (recur (next interfaces)))))))