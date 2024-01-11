(ns wgctrl.ssh.peers
  (:require [clojure.edn :as edn]
    [clojure.java.shell :refer [sh]]
    [clojure.string :as str]
    [wgctrl.ssh.common :as ssh]
    [wgctrl.cluster.ipcalc :as ip]
    
    
    [clojure.tools.logging :refer [info]])
  (:use [clojure.walk :only [keywordize-keys]]))


(defn peer! [node ip]
  (let [{:keys [exit out err]} (ssh/run-remote-script-sh-docker node "./scripts/create-peer.sh" ip)]
    (if (zero? exit)
      (edn/read-string out)
      (do (info "Error restore peer: "  err)
        err))))


(defn restore! [node peer ip]
  (let [{:keys [exit out err]} (ssh/run-remote-script-sh-docker node "./scripts/restore-peer.sh" peer ip)]
    (if (zero? exit)
      (edn/read-string out)
      (do (info "Error restore peer: "  err)
        err))))


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
            (recur (drop 2 ps) 
              (conj acc {:node-id (:uuid node)
                         :peer  (first (vals (first (take 2 ps))))
                         :ip (first (vals (second (take 2 ps))))}))))))))

(defn valid-peers [peers]
  (filter #(ip/ip-address? (ip/addr (:ip %))) peers))




(comment "Fix this "
  (defn delete-peer [^String endpoint ^String interface ^String peer]
    (-> (sh "ssh" endpoint "wg" "set" interface "peer" peer "remove") :out))


  (defn latest-peers [e i]
    (->> (-> (sh "ssh" e "wg" "show" i "latest-handshakes") :out
           (str/split #"\n"))
      (map #(str/split % #"\t"))
      (map #(zipmap [:peer :latest] %)))))
