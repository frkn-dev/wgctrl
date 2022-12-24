(ns wgctrl.cluster.model
  (:gen-class)
  (:require [wgctrl.utils.utils :as u]
            [wgctrl.cluster.transforms :as t]))

(defrecord Interface [name subnet endpoint port key peers])
(defrecord Peer [uuid name psk public-key private-key allowed-ips])
(defrecord Node [uuid hostname interfaces dns location status])
(defrecord Cluster [uuid nodes type])

(defn cluster!
  "Creates Cluster instance"
  ([]
   (->Cluster (u/uuid) (atom []) "standard"))
  ([type]
   (->Cluster (u/uuid) (atom []) type)))

(def cluster (cluster!))

(defn peer!
  "Creates Peer instance"
  [data]
  (apply ->Peer data))

(defn interface!
  "Creates Interface instance"
  [data]
  (->Interface (:name data) (-> data :subnet) (-> data :endpoint) (:port data) (:key data) (atom [])))

(defn node!
  "Creates Node instance"
  [data]
  (let [interfaces (map #(interface! %) (:interfaces data))
        node       (->Node (:uuid data) (:hostname data) (atom []) (:dns data) (:location data) "active")]
    (reduce (fn [n i]
              (t/interface->node i n)) node interfaces)))






