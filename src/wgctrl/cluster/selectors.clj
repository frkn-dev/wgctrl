(ns wgctrl.cluster.selectors
  (:require [wgctrl.cluster.checks :as c]
            [wgctrl.cluster.utils :as u]))

(defn interface-by-name [name node]
  (first (filter #(= name (:name %)) @(.interfaces node))))

(defn nodes-by-location [nodes location]
  (filter #(= location (-> % .location :code)) nodes))

(defn uuid-nodes-by-location [nodes location]
  (let [n (->> nodes (filter #(= location (-> % :location))))]
    (mapv #(zipmap [:uuid :weight :hostname :location] [(:uuid %) (:weight %) (:hostname %) (-> % :location)]) n)))

(defn node-by-uuid [nodes uuid]
  (filter #(= uuid (-> % .uuid)) nodes))

(defn node-by-uuid [nodes uuid]
  (first (filter #(= uuid (.uuid %)) nodes)))

(defn interface-by-type [node type]
  nil)

(defn available-locations [nodes]
  (set (map #(:location %) nodes)))

(defn active-nodes [cluster]
  (filter #(c/node-active?) @(.nodes cluster)))

(defn node-with-min-peers
  "Detects node with less peers"
  ([nodes location]
   (let [location-nodes (nodes-by-location nodes location)]
     (reduce (fn [n n']
               (if (< (u/node-size n) (u/node-size n'))
                 n
                 n')) (first location-nodes) location-nodes))))

(defn interface-with-min-peers
  "Detects interface with less peers"
  [node]
  (if (empty? node)
    nil
    (let [interfaces @(.interfaces node)]
      (if (= 1 (count interfaces))
        (first interfaces)
        (reduce (fn [i i']
                  (if (< (u/interface-size i) (u/interface-size i'))
                    i
                    i')) (first interfaces) interfaces)))))

(defn peers-never-connected [data]
  (->> data
       (filter #(nil? (:latest %)))
       (map #(:peer %))))

(defn peers-connected [data]
  (->> data
       (remove #(nil? (:latest %)))

       (map #({:peer (:peer %)
               :latest (:latest %)
               :traffic (:traffic %)}))))




