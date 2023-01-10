(ns wgctrl.cluster.utils
  (:require [wgctrl.cluster.ipcalc :as ip]
            [clojure.java.io :as io]
            [clojure.edn :as edn]))

(defn interface-size
  "Count of peers on iface"
  [iface]
  (count @(.peers iface)))

(defn node-size
  "Calculates size of node -> sum if all peers"
  [node]
  (reduce (fn [n i] (+ (interface-size i) n))
          0 @(.interfaces node)))

(defn endpoints4
  "Gets inet endpoint address"
  [node]
  (map #(-> % :endpoint :inet) (.interfaces node)))

(defn addr!
  "Calculates available IP address for client"
  [interface]
  (if (nil? interface)
    nil
    (let [subnet (-> interface .subnet :inet)
          size (interface-size interface)
          addresses (->> (map (fn [x]
                                (-> x
                                    .allowed-ips
                                    ip/addr
                                    ip/addr->int)) (filter #(not (nil? (.allowed-ips %))) @(.peers interface)))
                         sort
                         (map #(ip/int->addr %)))]
      (cond
        (= 0 size) (ip/addr++ (ip/addr subnet))
        (>= size (ip/size (ip/mask subnet))) nil
        :else (ip/addr++ (reduce (fn [a a'] (if (> (Math/abs (ip/addr- a' a)) 1)
                                              a
                                              a')) (first addresses) (rest addresses)))))))


(defn load-edn
  "Load edn from an io/reader source (filename or io/resource)."
  [source]
  (try
    (with-open [r (io/reader source)]
      (edn/read (java.io.PushbackReader. r)))
    (catch java.io.IOException e
      (printf "Couldn't open '%s': %s\n" source (.getMessage e)))
    (catch RuntimeException e
      (printf "Error parsing edn file '%s': %s\n" source (.getMessage e)))))

