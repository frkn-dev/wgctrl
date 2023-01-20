(ns wgctrl.cluster.state
  (:require [wgctrl.cluster.ssh :as ssh]
            [wgctrl.cluster.transforms :as t]
            [wgctrl.cluster.selectors :as s]
            [wgctrl.cluster.model :as m]
            [wgctrl.cluster.balancer :as b]

            [clojure.core.async :refer [chan timeout go-loop <! >! >!! <!!]]))

(defonce cluster (m/cluster!))

(defn- restore-peers [c]
  (for [node @(.nodes c)]
    (dorun (for [interface @(.interfaces node)]
     (let [connstr  (str "root@" (-> interface .endpoint :inet))
          iface (-> interface .name)]
             (reduce (fn [interface p]
                   (t/peer->interface (m/peer! p) interface)) interface (ssh/peers connstr iface)))))))

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
  (dorun (restore-peers cluster))
  
   ; Create initial balancers
  (dorun (b/balancer! cluster)))

