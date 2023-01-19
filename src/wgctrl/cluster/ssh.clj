(ns wgctrl.cluster.ssh
  (:require [clojure.edn :as edn]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [wgctrl.cluster.selectors :as s])
  (:use [clojure.walk :only [keywordize-keys]]))

(defn node-reg-data
  "Gets data from node"
  [node]
  (let [{:keys [location dns weight]} node]
   ; (println location dns weight)
    (-> (shell/sh "ssh" (:address node) "cat" "/root/.wg-node")
        :out
        (edn/read-string)
        (conj {:location location})
        (conj {:dns dns})
        (conj {:weight weight}))))

(defn peer!
  "Creates peer on WG node"
  [pubkey interface ip]
  (shell/sh "ssh" (str "root@" (-> interface .endpoint :inet))
            "wg" "set" (.name interface)
            "peer" pubkey
            "allowed-ips" (str ip "/32")))

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
  (let [{:keys [err exit out]} (shell/sh "ssh" endpoint "wg" "showconf" interface)]
    (if (= exit 0 )
      (->> (str/split out #"\[Peer\]\n")
         (drop 1) ; drop interface record
         (map #(str/split % #"\n"))
         (map (fn [x]  (map #(str/split % #" = ") x)))
         (map (fn [x] (map #(hash-map (keyword (str/lower-case (first %))) (last %))x)))
         (map (fn [x] (apply conj x)))
         (map (fn [x] {:peer (:publickey x) :ip (:allowedips x)})))
      nil)))

(defn delete-peer [^String endpoint ^String interface ^String peer]
  (-> (shell/sh "ssh" endpoint "wg" "set" interface "peer" peer "remove") :out))












