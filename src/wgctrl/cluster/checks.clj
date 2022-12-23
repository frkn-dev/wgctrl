(ns wgctrl.cluster.checks
  (:require [wgctrl.cluster.model :as m]
            [wgctrl.cluster.utils :as u]))

(defn cluster? [c]
  (instance? wgctrl.cluster.model.Cluster c))

(defn peer? [p]
  (instance? wgctrl.cluster.model.Peer p))

(defn interface? [i]
  (instance? wgctrl.cluster.model.Interface i))

(defn node? [n]
  (instance? wgctrl.cluster.model.Node n))

(defn node-reg-data-valid? [data]
  (empty? (filter #(empty? (% data))
                  [:uuid
                   :default-interface
                   :interfaces
                   :location
                   :dns])))

(defn node-active? [node]
  (= "active" (.status node)))

(defn node-registered? [cluster endpoint]
  (filter #(= endpoint (u/endpoints4 %))) (.nodes cluster))



