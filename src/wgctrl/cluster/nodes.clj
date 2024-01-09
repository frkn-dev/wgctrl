(ns wgctrl.cluster.nodes
  (:require [mount.core :refer [defstate]]  
    [wgctrl.config :refer [config]]
    [wgctrl.ssh.nodes :as ssh-nodes]
    [clojure.tools.logging :as log]))

(defrecord Node [uuid location dns type hostname default-interface user endpoint active weight interface])
(defrecord Interface [name subnet port public-key psk container ])

(defn wg! [{:keys [name subnet port public-key psk container_id]}]
  (->Interface name subnet port public-key psk container_id))

(defn node! [{:keys [uuid location dns type hostname default-interface user endpoint active weight wg]}]
  (log/info (str uuid " " location " " dns " " type " " hostname " "  default-interface " " user " " endpoint " " active " " weight " " wg))
  (->Node uuid location dns type hostname default-interface user endpoint active weight (wg! wg)))

(defn nodes-> [config]
  (reduce (fn [cluster' data']
            (conj cluster' (node! (ssh-nodes/node-reg-data data'))))
    [] (filter #(true? (:active %)) (-> config :nodes))))

(defstate nodes :start (nodes-> config)
  :stop [])


(defn node-by-uuid [nodes uuid]
  (->> nodes
    (filter #(= uuid (:uuid %)))
    first))

(defn nodes-active [nodes]
  (filter #(true? (:active %)) nodes))

(defn nodes-by-location [nodes location]
  (filter #(= location (-> % .location)) nodes))

(defn locations [n]
  (set (map #(:location %) n)))


