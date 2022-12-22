(ns wgctrl.cluster.model
	(:gen-class)
	(:require [wgctrl.utils.main :as u]
            [wgctrl.cluster.transforms :as t]))

(defrecord Interface [name subnet endpoint port peers])
(defrecord Peer [uuid name psk public-key private-key allowed-ips])
(defrecord Node [uuid hostname interfaces dns location status])
(defrecord Cluster [uuid nodes type])

(defonce cluster (cluster!))

(defn cluster! 
  "Creates Cluster instance"
  ([]
    (->Cluster (u/uuid) (atom []) "standard" ))
  ([type]
    (->Cluster (u/uuid) (atom []) type )))

(defn peer! 
  "Creates Peer instance"
  [data]
  (apply ->Peer data))

(defn interface!
  "Creates Interface instance"
  [data]
  (->Interface (:name data) (-> data :subnet ) (-> data :endpoint ) (:port data) (atom [])))

(defn node! 
  "Creates Node instance"
  [data]
  (let [interfaces (map #(interface! %) (:interfaces data))
        node       (->Node (:uuid data) (:hostname data) (atom []) (:dns data) (:location data) "active")]
    (reduce (fn [n i]
               (t/interface->node i n)) node interfaces)))






