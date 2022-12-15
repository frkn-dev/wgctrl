(ns wg-agent.cluster 
	(:gen-class)
	(:require [clojure.string :as str]
		      [clojure.java.shell :as shell]
		      [wg-agent.wg :as wg]
		      [wg-agent.ip :as ip]
		      [wg-agent.ipcalc :as ic]))


(defrecord Interface [name subnet endpoint port])
(defrecord Peer [uuid ^Interface interface name psk public-key private-key allowed-ips])
(defrecord Node [uuid interfaces dns peers])
(defrecord Cluster [id nodes location])


(defn valid? 
  "Checks interface is valid (port shouldn't be nil)"
  [interface]
  (not (nil? (:port interface))))

(defn which-node? 
  "Detects node with less peers"
  [cluster]
  (let [nodes (.nodes cluster )]
    (reduce (fn [n1 n2]
      (if (< (count (.peers n1)) (count (.peers n2)))
        n1
        n2)) (first nodes) nodes)))

(defn interface-size 
  "Count of peers on iface"
  [iface node]
  (count 
    (filter #(= iface (.name (.interface %))) (.peers node))))

(defn which-interface? 
  "Detects interface with less peers"
  [node]
  (let [interfaces (.interfaces node)]
    (if (= 1 (count interfaces))
      (first interfaces)
      (do (reduce (fn [if1 if2]
        (if (< (wg-iface-size  (.name if1) node) (wg-iface-size  (.name if2) node))
          if1
          if2)) (first interfaces) interfaces)))))

(defn last-nodes 
  "All cluster's nodes except chosen"
  [cluster node]
  (filter #(not(= (.uuid node) (.uuid %))) (.nodes cluster)))

(defn node->cluster 
  "Adds node to cluster"
  [node cluster]
  (->Cluster (.id cluster) (conj (.nodes cluster) node) (.location cluster))) 

(defn peer->cluster
  "Adds peer to cluster's node"
 [peer cluster]
  (let [node (which-node? cluster)
        last (vec (last-nodes cluster node))]
    (let [updated (update-in node [:peers] conj peer)
          cluster-upd (->Cluster (.id cluster) last (.location cluster))]
      (update-in cluster-upd [:nodes] conj updated))))

(defn interfaces!
  "Generates instance of all available interfaces"
  []
  (let [interfaces (wg/interfaces)]
    (mapv (fn [x] (->Interface x (wg/subnet x) (ip/endpoint4) (wg/port x))) interfaces)))

(defn node! 
  "Creates node instance"
  [config]
  (let [iface (wg-iface-valid (wg-interfaces!))
        u (uuid)
        dns (:dns config)
        peers []]
    (->Node u iface dns peers)))

(defn cluster! 
  "Creates cluster instance"
  [location]
  (let [u (uuid)]
    (->Cluster u [] location)))

(defn addr! 
  "Calculates available IP address for client"
  [node]
  (let [subnet (.subnet (which-interface? node))
        node-size (count (.peers node))
        addresses' (map #(int->addr %) 
                           (sort (map (fn [p] (addr->int (subnet-addr (.allowed-ips p)))) (.peers node))))]

              (cond 
                (= 0 node-size) (addr++ (subnet-addr (:inet subnet)))
                (>= node-size (subnet-size (subnet-mask (:inet subnet)))) nil
                :else (addr++ (reduce (fn [a a'] (if (> (Math/abs (addr- a' a)) 1)
                                             a
                                             a')) (first addresses') (rest addresses'))))))


