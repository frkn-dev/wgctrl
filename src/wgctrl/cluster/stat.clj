(ns wgctrl.cluster.stat
  (:require [wgctrl.cluster.nodes :refer [nodes]]
    [clojure.string :as str]
    [clojure.java.shell :refer [sh]]
    [wgctrl.cluster.ipcalc :as ip]))

(defn seconds [timestamp]
  (let [now (quot (System/currentTimeMillis) 1000)]
    (- now (Integer/parseInt timestamp))))

(defn traffic [peers]
  {:tx (->> peers (map #(parse-long (:tx %))) (reduce +) str)
   :rx (->> peers (map #(parse-long (:rx %))) (reduce +) str)})

(defn peers-dump [node]
  (->> 
    (-> (sh "ssh" (str (:user node) "@" (:endpoint node)) 
          "docker" "exec" (-> node :interface :container) 
          "wg" "show" (-> node :interface :name) "dump") 
      :out
      (str/split #"\n\n"))
    
    (map #(str/split % #"\n"))
    (map (fn [x] (map #(str/split % #"\t") x)))
    first
    (drop 1)
    (map #(zipmap [:peer :psk :endpoint :allowed :latest :tx :rx] %))))

(defn peers [node]
  (->> 
    (-> (sh "ssh" (str (:user node) "@" (:endpoint node)) 
          "docker" "exec" (-> node :interface :container) 
          "wg" "show" (-> node :interface :name)) 
      :out
      (str/split #"\n\n"))
    (map #(str/split % #"\n"))
    (map (fn [x] (map #(str/split % #": ") x)))
    (drop 1)  ; drop interface record
    (map (fn [x] (map #(drop 1 %) x)))  ; drop keys 
    (mapv #(apply concat %))
    (map #(zipmap [:peer :psk :endpoint :allowed :latest :traffic] %))))

(defn peers-alive [peers]
  (->> peers
    
    (map #(assoc (dissoc % :latest) :latest (seconds (:latest %))))
    (filter #(ip/ip-address? (ip/addr (:endpoint %))))
    (filter #(< (:latest %) 300))
    (map #(assoc % :endpoint "HIDDEN"))))


(defn peers-stat
  "Stat peers "
  [node]
  (->> (peers node)
    (map #(assoc % :endpoint "HIDDEN"))
    (remove #(or (nil? (:latest %))
               (nil? (:traffic %))))))


(defn peers-amount [node]
  "Amount peers"
  [node]
  (let [peers (peers-dump node)
        live-peers (peers-alive peers)]
    (conj {:total (count peers) :live (count live-peers)} 
      (traffic live-peers))))


