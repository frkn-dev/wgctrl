
(ns wgctrl.cluster.peers
  (:require [wgctrl.ssh.peers :as ssh]
    [wgctrl.cluster.ipcalc :as ip]
    [clojure.tools.logging :as log]))


(defrecord Peer [peer node-id type allowed-ips created-at])

(defn peer!
  "Creates Peer instance"
  [{:keys [peer ip node-id] }]
  (->Peer peer node-id "standard" ip (System/currentTimeMillis)))

(defn restore-peers [nodes]
  (reduce (fn [acc p] (conj acc (peer! p))) [] (mapcat #(ssh/peers %) nodes)))



(defn addr!
  "Calculates available IP address for peer"
  [node]
  
  (let [subnet (-> node :interface :subnet)
        peers' (restore-peers [node])
        amount (count peers')
        addresses (->> (map (fn [x]
                              (-> x
                                .allowed-ips
                                ip/addr
                                ip/addr->int)) (filter #(and (not(= "(none)" (.allowed-ips %)))
                                                          (not (nil? (.allowed-ips %)))) peers'))
                    sort
                    (map #(ip/int->addr %)))]
    (cond 
      (= 0 amount) (ip/addr++ (ip/addr subnet))
      (>= amount (ip/size (ip/mask subnet))) nil
      :else (ip/addr++ (reduce (fn [a a'] (if (> (Math/abs (ip/addr- a' a)) 1)
                                            a
                                            a')) (first addresses) (rest addresses))))))

