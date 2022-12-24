(ns wgctrl.cluster.checks
  (:require [wgctrl.cluster.utils :as u]))

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

