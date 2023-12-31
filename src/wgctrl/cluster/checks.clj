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

(defn peer-exists? [pubkey interface]
  (not (empty? (filter #(= pubkey (.peer %)) @(.peers interface)))))

(defn node-exists? [node cluster]
  (not (empty? (filter #(= (:uuid node) (.uuid %)) @(.nodes cluster)))))

(defn node-available? [node config]
  (let [e (-> node .interfaces deref first .endpoint)]
    (not (empty? (filter #(and (true? (:active %))
                            (= e (:address %))) (-> config deref :nodes))))))
