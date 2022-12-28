(ns wgctrl.cluster.model
  (:require [wgctrl.cluster.transforms :as t]))

(defrecord Interface [name subnet endpoint port key peers])
(defrecord Peer [peer psk allowed-ips])
(defrecord Node [uuid hostname interfaces dns location status])
(defrecord Cluster [uuid nodes type])

(defn cluster!
  "Creates Cluster instance"
  ([]
   (->Cluster (.toString (java.util.UUID/randomUUID)) (atom []) "standard"))
  ([type]
   (->Cluster (.toString (java.util.UUID/randomUUID)) (atom []) type)))

(defonce cluster (cluster!))

(defn peer!
  "Creates Peer instance"
  [data]
  (apply ->Peer data))

(defn interface!
  "Creates Interface instance"
  [data]
  (->Interface (:name data) (-> data :subnet) (-> data :endpoint) (:port data) (:public-key data) (atom [])))

(defn node!
  "Creates Node instance"
  [data]
  (let [interfaces (map #(interface! %) (:interfaces data))
        node       (->Node (:uuid data) (:hostname data) (atom []) (:dns data) (:location data) "active")]
    (reduce (fn [n i]
              (t/interface->node i n)) node interfaces)))




