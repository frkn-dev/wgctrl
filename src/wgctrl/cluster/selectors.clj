(ns wgctrl.cluster.selectors
	(:require [wgctrl.cluster.checks :as c])
	(:gen-class))


(defn interface-by-name [name node]
  (first (filter #(= name (:name %)) @(.interfaces node))))

(defn nodes-by-location [location cluster]
  (filter #(= location (.location %)) @(.nodes cluster)))

(defn interface-by-type [node type]
	nil)

(defn active-nodes [cluster]
	(filter #(c/node-active?) @(.nodes cluster)))

(defn node-with-min-peers
	"Detects node with less peers"
  ([nodes]
    (reduce (fn [n n']
      (if (< (u/node-size n) (u/node-size n'))
        n
        n')) (first nodes) nodes))
  ([nodes location]
    (let [location-nodes (nodes-by-location location nodes))]
      (reduce (fn [n n']
        (if (< (u/node-size n) (u/node-size n'))
          n
          n')) (first location-nodes) location-nodes))))

(defn interface-with-min-peers
  "Detects interface with less peers"
  [node]
  (let [interfaces @(.interfaces node)]
    (if (= 1 (count interfaces))
      (first interfaces)
      (reduce (fn [i i']
        (if (< (u/interface-size i) (u/interface-size i'))
          i
          i')) (first interfaces) interfaces))))