(ns wgctrl.ssh.peers
  (:require [clojure.edn :as edn]
    [clojure.java.shell :refer [sh]]
    [clojure.string :as str]
    [wgctrl.ssh.common :as ssh])
  (:use [clojure.walk :only [keywordize-keys]]))


(defn peer! [node ip]
  (-> (ssh/run-remote-script-sh-docker node "./scripts/create-peer.sh" ip)
    :out 
    (edn/read-string)))


(defn peers
  "Gets real peers from WG interface "
  [node]
  (let [e (str (:user node) "@" (:endpoint node))
        c (-> node :interface :container)
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
            (recur (drop 2 ps) (conj acc {:node-id (:uuid node) :peer  (first (vals (first (take 2 ps)))) :ip (first (vals (second (take 2 ps))))}))))))))



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
