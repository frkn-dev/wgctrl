(ns wgctrl.cluster.stat
  (:require [wgctrl.cluster.nodes :refer [nodes]]
    [clojure.string :as str]
    [clojure.java.shell :refer [sh]]))

(defn node-stat [nodes]
  nodes)



(defn peers-stat
  "Stat peers "
  [node]
  (->> (-> (sh "ssh" (str (:user node) "@" (:endpoint node)) "docker" "exec" (-> node :interface :container) "wg" "show" (-> node :interface :name)) :out
         (str/split #"\n\n"))
    (map #(str/split % #"\n"))
    (map (fn [x] (map #(str/split % #": ") x)))
    (drop 1)  ; drop interface record
    (map (fn [x] (map #(drop 1 %) x)))  ; drop keys 
    (mapv #(apply concat %))
    (map #(zipmap [:peer :psk :endpoint :allowed :latest :traffic] %))
    (map #(assoc % :endpoint "HIDDEN"))
    (remove #(or (nil? (:latest %))
               (nil? (:traffic %))))))


(defn peers-amount [node]
  "Gets real peers from WG interface "
  [node]
  (->> (-> (sh "ssh" (str (:user node) "@" (:endpoint node)) "docker" "exec" (-> node :interface :container) "wg" "show" (-> node :interface :name)) :out
         (str/split #"\n\n"))
    (map #(str/split % #"\n"))
    (map (fn [x] (map #(str/split % #": ") x)))
    (drop 1)
    count)) 
