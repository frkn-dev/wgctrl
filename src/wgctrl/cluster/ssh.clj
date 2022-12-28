(ns wgctrl.cluster.ssh
  (:require [clojure.edn :as edn]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [wgctrl.cluster.selectors :as s])
  (:use [clojure.walk :only [keywordize-keys]]))

(defn node-reg-data
  "Gets data from node"
  [node]
  (-> (shell/sh "ssh" (:address node) "cat" "/root/.wg-node")
      :out
      (edn/read-string)
      (conj {:location (:location node)})
      (conj {:dns (:dns node)})))

(defn peer!
  "Creates peer on WG node"
  [keys interface ip]
  (let [pubkey (:server-pubkey keys)
        f (str/replace (:client-psk keys) #"/" "")]

    (spit (str "/tmp/" f) (:client-psk keys))
    (let [{:keys [err out exit]}
          (shell/sh "scp" (str "/tmp/" f)
                    (str "root@" (:inet (.endpoint interface)) ":/tmp"))]

      (if (= 0 exit)
        (shell/sh "ssh" (str "root@" (:inet (.endpoint interface)))
                  "wg" "set" (.name interface)
                  "peer" (:client-pubkey keys)
                  "allowed-ips" (str ip "/32")
                  "preshared-key"  (str "/tmp/" f))
        {:err err :out :exit}))))

(defn peers-stat
 "Gets real peers from WG interface "
 [^String endpoint ^String interface]
  (->> (-> (shell/sh "ssh" endpoint "wg" "show" interface) :out
           (str/split #"\n\n"))
        (map #(str/split % #"\n"))
        (map (fn [x] (map #(str/split % #": ") x))) 
        (drop 1)  ; drop interface record
        (map (fn [x] (map #(drop 1 %) x)))  ; drop keys 
        (mapv #(apply concat %)) 
        (map #(zipmap [:peer :psk :endpoint :allowed :latest :traffic] %))))

(defn peers
 "Gets real peers from WG interface "
 [^String endpoint ^String interface]
    (let [peers (-> (shell/sh "ssh" endpoint "wg" "showconf" interface) :out)]
      (->> (str/split peers #"\[Peer\]\n")
           (drop 1) ; drop interface record
           (map #(str/split % #"\n"))
           (map (fn [x]  (map #(str/split % #" = ") x)))
           (map (fn [x] (map #(last %) x))) ; drop keys
           (map #(zipmap [:peer :psk :ip :endpoint ] %)))))

(defn delete-peer [^String endpoint ^String interface ^String peer]
  (-> (shell/sh "ssh" endpoint "wg" "set" interface "peer" peer "remove") :out))












