(ns wgctrl.cluster.ssh
  (:require [clojure.edn :as edn]
    [clojure.java.shell :refer [sh]]
    [clojure.string :as str]
    [wgctrl.cluster.selectors :as s])
  (:use [clojure.walk :only [keywordize-keys]]))


(defn run-remote-script-bb [node script]
  (let [{:keys [address user]} node
        remote-command (str "ssh "user "@" address " 'bb' < " script)]
    (sh "/bin/bash" "-c" remote-command)))

(defn run-remote-script-sh-docker [interface script ip]
  (let [remote-command (str "ssh root@" (:endpoint interface) " 'docker exec -i' " (:container interface) " bash -s < " script " " ip)]
    (sh "/bin/bash" "-c" remote-command)))

(defn register-node [node]
  (run-remote-script-bb node "./scripts/node-register-amnezia.bb"))

(defn node-registered? [node]
  (let [{:keys [exit]} (run-remote-script-bb node "./scripts/node-registered-check.bb")]
    (if (zero? exit)
      true
      false)))

(defn node-reg-data
  "Gets data from node or register node"
  [node]
  (let [{:keys [address user location dns weight]} node]
    ; (println location dns weight)
    (if (node-registered? node) 
      (-> (sh "ssh" (str user "@" address) "cat" "/root/.wg-node")
        :out
        (edn/read-string)
        (conj {:location location})
        (conj {:dns dns})
        (conj {:weight weight}))
      (do (register-node node)
        (node-reg-data node)))))


(defn peer! [node ip]
  (-> (run-remote-script-sh-docker node "./scripts/create-peer.sh" ip)
    :out 
    (edn/read-string)))


(defn peers
  "Gets real peers from WG interface "
  [interface]
  (let [e (str "root@" (-> interface :endpoint))
        c (:container interface)
        {:keys [err exit out]} (sh "ssh" e "docker" "exec"  c "wg")]
    (if (= exit 0 )
      (let [raw (->> (str/split out #"\n")
                  (filter #(or (str/starts-with? % "peer")
                             (str/includes? % "allow")))
                  (map #(str/split % #":"))
                  (mapv (fn [[k v]] { (keyword (str/replace (str/trim k) #" " "")) (str/trim v) })))]
          
        (loop [ps raw
               acc []]
          (if (empty? ps)
            acc
        
              
            (recur (drop 2 ps) (conj acc {:peer  (first (vals (first (take 2 ps)))) :ip (first (vals (second (take 2 ps))))}))))))))



(comment "Fix this "
  (defn delete-peer [^String endpoint ^String interface ^String peer]
    (-> (sh "ssh" endpoint "wg" "set" interface "peer" peer "remove") :out))


  (defn peers-stat
    "Gets real peers from WG interface "
    [^String endpoint ^String interface]
    (->> (-> (sh "ssh" endpoint "wg" "show" interface) :out
           (str/split #"\n\n"))
      (map #(str/split % #"\n"))
      (map (fn [x] (map #(str/split % #": ") x)))
      (drop 1)  ; drop interface record
      (map (fn [x] (map #(drop 1 %) x)))  ; drop keys 
      (mapv #(apply concat %))
      (map #(zipmap [:peer :psk :endpoint :allowed :latest :traffic] %))))

  (defn latest-peers [e i]
    (->> (-> (sh "ssh" e "wg" "show" i "latest-handshakes") :out
           (str/split #"\n"))
      (map #(str/split % #"\t"))
      (map #(zipmap [:peer :latest] %)))))









