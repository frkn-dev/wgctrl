(ns wgctrl.cluster.state
  (:require [wgctrl.cluster.ssh :as ssh]
    [wgctrl.cluster.transforms :as t]
    [wgctrl.cluster.selectors :as s]
    [wgctrl.cluster.model :as m]
    [wgctrl.cluster.balancer :as b]
    [wgctrl.cluster.checks :as c]
    [clojure.tools.logging :as log]

    [clojure.core.async :refer [chan timeout go-loop <! >! >!! <!!]]))

(defonce cluster (m/cluster!))


(defn- restore-peers [c]
  (for [node @(.nodes c)]
    (dorun (for [interface @(.interfaces node)]     
             (reduce (fn [interface p]
                       (t/peer->interface (m/peer! p) interface)) interface (ssh/peers interface))))))


(defn restore-state
  "Restores state of WG Cluster, adds Nodes with Interfaces according config"
  [config]
  ; Checking and adding nodes
  
    
    (log/info "[DEBUG] Updating state")
  
    (reduce (fn [cluster' data']
              (t/node->cluster (m/node! (ssh/node-reg-data data')) cluster'))
      cluster (filter #(true? (:active %)) (-> config deref :nodes)))
    
  
    ; Check non active or deleted nodes
  
    (dorun (restore-peers cluster))

    ; Create initial balancers
    (dorun (b/balancer! cluster))
  )

