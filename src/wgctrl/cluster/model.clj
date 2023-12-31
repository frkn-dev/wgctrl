(ns wgctrl.cluster.model
  (:require [wgctrl.cluster.transforms :as t]))

(defrecord Interface [name subnet endpoint port key psk container peers])
(defrecord Peer [peer allowed-ips created-at])
(defrecord Node [uuid hostname interfaces dns location active weight])
(defrecord Cluster [uuid nodes type balancer])


(defn cluster!
  "Creates Cluster instance"
  ([]
   (->Cluster (.toString (java.util.UUID/randomUUID)) (atom []) "standard" (atom {})))
  ([type]
   (->Cluster (.toString (java.util.UUID/randomUUID)) (atom []) type (atom {}))))

(defn peer!
  "Creates Peer instance"
  [{:keys [peer ip]}]
  (->Peer peer ip (System/currentTimeMillis)))

(defn interface!
  "Creates Interface instance"
  [data]
  (let [{:keys [name subnet endpoint port public-key psk container_id]} data]
    (->Interface name
      subnet
      endpoint
      port
      public-key
      psk
      container_id
      (atom []))))

(defn node!
  "Creates Node instance"
  [data]
  (let [{:keys [uuid hostname dns interfaces location weight]} data]
    (let [interfaces (mapv #(interface! %) interfaces )
          node       (->Node uuid hostname (atom []) dns (or location "dev") true (or weight 10))]
      (reduce (fn [n i]
                (t/interface->node i n)) node interfaces))))




