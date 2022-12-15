(ns wg-agent.core
  (:gen-class)
  (:require [org.httpkit.server :as httpkit]
            [compojure.core :refer [defroutes GET]]
            [compojure.route :as route]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.core.async :as async :refer [go]]
)
  (:use [clojure.walk :only [keywordize-keys]]))


(defrecord Interface [name subnet endpoint port])
(defrecord Peer [uuid ^Interface interface name psk public-key private-key allowed-ips])
(defrecord Node [uuid interfaces dns peers])
(defrecord Cluster [id nodes location])

(defn uuid [] (java.util.UUID/randomUUID))

(def config {:dns "1.1.1.1"})

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

(defn ubuntu? 
  "Returns true if Ubuntu, "
  []
  (try 
    (if (= "Ubuntu" (-> (shell/sh "lsb_release" "-si") :out str/trim-newline ))
      true
      false)
  (catch java.io.IOException e
    false)))

(defn default-interface 
  "Parses output of 'ip -json route and gets default interface;"
  [] 
  (if (ubuntu?)
    (let [routes (-> (shell/sh "ip" "-json" "route") 
                   :out
                 json/parse-string
                 keywordize-keys)]
        (:dev (nth (filter #(= (:dst %) "default") routes) 0)))
     "ens3"))


(defn endpoint4
  "Parses output of 'ip -json route and gets default interface;"
  [] 
  (let [ifaces (-> (shell/sh "ip" "-json" "address" "show" (default-interface)) 
                   :out
                   json/parse-string
                   keywordize-keys
                   vec)]
     (:local (first (filter #(= (:family %) "inet") (get-in ifaces [0 :addr_info]))))))

(defn endpoint6
  "Parses output of 'ip -json route and gets default interface;"
  [] 
  (let [ifaces (-> (shell/sh "ip" "-json" "address" "show" (default-interface)) 
                   :out
                   json/parse-string
                   keywordize-keys
                   vec)]
     (:local (first (filter #(= (:family %) "inet6") (get-in ifaces [0 :addr_info]))))))


(defn peer!
  "Should make peer for WG"
 [node name]
  (shell/sh "bash" "add-peer.sh" (interface) (endpoint) (allowed-ips) (name) (port)))

(defn node->cluster 
  "Adds node to cluster"
  [node cluster]
  (->Cluster (.id cluster) (conj (.nodes cluster) node) (.location cluster))) 


(defn which-node? 
  "Detects node with less peers"
  [cluster]
  (let [nodes (.nodes cluster )]
    (reduce (fn [n1 n2]
      (if (< (count (.peers n1)) (count (.peers n2)))
        n1
        n2)) (first nodes) nodes)))


(defn wg-iface-size 
  "Count of peers on iface"
  [iface node]
  (count 
    (filter #(= iface (.name (.interface %))) (.peers node))))

(defn which-interface? 
  "Detects interface with less peers"
  [node]
  (let [interfaces (.interfaces node)]
    (if (= 1 (count interfaces))
      (first interfaces)
      (do (reduce (fn [if1 if2]
        (if (< (wg-iface-size  (.name if1) node) (wg-iface-size  (.name if2) node))
          if1
          if2)) (first interfaces) interfaces)))))

(defn last-nodes 
  "All cluster's nodes except chosen"
  [cluster node]
  (filter #(not(= (.uuid node) (.uuid %))) (.nodes cluster))
  )

(defn peer->cluster
  "Adds peer to cluster's node"
 [peer cluster]
  (let [node (which-node? cluster)
        last (vec (last-nodes cluster node))]
    (let [updated (update-in node [:peers] conj peer)
          cluster-upd (->Cluster (.id cluster) last (.location cluster))]
      (update-in cluster-upd [:nodes] conj updated))))

(defn wg-interfaces []
  (let [ifaces (str/split (-> (shell/sh "ls" "/etc/wireguard/") :out) #"\n")]
    (mapv #(str/replace % #".conf" "") ifaces)))

(defn wg-iface-port [iface]
  (let [path "/etc/wireguard/"]
    (let [line (->> (str/split (slurp (str path iface ".conf")) #"\n")
      (map #(str/split % #" "))
      (filter (fn [x] (= (first x) "ListenPort"))))]
    (last (first line)))))

(defn wg-subnet
  "Reads config of wg interface and gets subnet"
  [iface]
  (let [path "/etc/wireguard/"]
    (let [line (->> (str/split (slurp (str path iface ".conf")) #"\n")
      (map #(str/split % #" "))
      (filter (fn [x] (= (first x) "Address")))
      first
      (filter (fn [x] (and (not (= x "Address" ))
                          (not (= x "=" )))))
      (map #(str/replace % #"," "")))]
      (zipmap [:inet :inet6] line))))

(defn wg-interfaces!
"Generates instance of all available interfaces"
 []
  (let [wg-ifaces (wg-interfaces)
        endpoint (endpoint4)]
    (mapv (fn [x] (->Interface x (wg-subnet x) endpoint (wg-iface-port x))) wg-ifaces)))

(defn wg-iface-valid 
  "Checks interfaces are valid (port shouldn't be nil)"
  [interfaces]
  (filter #(not (nil? (:port %))) interfaces))

(defn node! 
  "Creates node instance"
  [config]
  (let [iface (wg-iface-valid (wg-interfaces!))
        u (uuid)
        dns (:dns config)
        peers []]
    (->Node u iface dns peers)))


(defn cluster! 
  "Creates cluster instance"
  [location]
  (let [u (uuid)]
    (->Cluster u [] location)))

(defn addr->vec 
  "Transforms IP address from string to vector"
  [a]
  (let [a' (->>
    (str/split a #"/")
    first)]

  (->>(str/split a' #"\.")
          (mapv #(Integer/parseInt %)))))

(defn subnet-mask 
  "Cuts mask of subnet from address"
  [subnet]
  (->>
    (str/split subnet #"/")
    last
    (Integer/parseInt)))

(defn subnet-addr 
  "Cuts address of subnet from mask"
  [subnet]
  (->>
    (str/split subnet #"/")
    first))

(defn subnet-size 
  "Calculates size of subnet by mask"
  [mask]
  (- (Math/pow 2 (- 32 mask)) 2))

(defn addr++ 
  "Increments IP address"
  [a]
  (let [a' (inc (addr->int a))]
        (if (> a' (Math/pow 2 32))
          nil
          (int->addr  a'))))

(addr++ "0.0.0.255")


          (addr->string [(bit-shift-right (bit-and (inc a') 0xff000000) 24) 
           (bit-shift-right (bit-and (inc a') 0x00ff0000) 16)
           (bit-shift-right (bit-and (inc a') 0x0000ff00) 8)
           (bit-and (inc a') 0x000000ff)]))))

(defn addr- 
  "Calculates range between 2 IP addresses "
  [a b]
  (let [a' (addr->int a)
        b' (addr->int b)]
    (- a' b')))

(defn addr->string 
  "Transofrms IP address from vector to string"
  [a]
  (if (nil? a)
    nil
    (str/join #"." a)))

(defn addr->int
  "Transforms IP address from string to uint" 
  [a]
  (if (nil? a)
    nil
    (let [a' (addr->vec a)]
    (bit-or 
              (bit-shift-left (nth a' 0) 24) 
              (bit-shift-left (nth a' 1) 16) 
              (bit-shift-left (nth a' 2) 8) 
              (nth a' 3)))))

(defn int->addr
  "Transforms IP address from uint to string"
  [i]
  (addr->string [(bit-shift-right (bit-and i 0xff000000) 24) 
           (bit-shift-right (bit-and i 0x00ff0000) 16)
           (bit-shift-right (bit-and i 0x0000ff00) 8)
           (bit-and i 0x000000ff)])
  )


(defn addr> 
  "Compares 2 IP addresses" 
  [a b]
  (let [a'  (addr->int a)
        b' (addr->int b)]

        (if (> a' b')
          true
          false)))


(defn addr! 
  "Calculates available IP address for client"
  [node]
  (let [subnet (.subnet (which-interface? node))
        node-size (count (.peers node))
        addresses' (map #(int->addr %) 
                           (sort (map (fn [p] (addr->int (subnet-addr (.allowed-ips p)))) (.peers node))))]

              (cond 
                (= 0 node-size) (addr+ (subnet-addr (:inet subnet)))
                (>= node-size (subnet-size (subnet-mask (:inet subnet)))) nil
                :else (addr++ (reduce (fn [a a'] (if (> (Math/abs (addr- a' a)) 1)
                                             a
                                             a')) (first addresses') (rest addresses'))))))



(let [c (cluster! "dev")
      n (node! {:dns "1.1.1.1"})]
  (node->cluster n c))




; 1) Agent register -> detect WG interfaces, subnet and endpoint/port
; 2) Make Cluster Instance + Node
; 3) Add Peer to Server and to Datastructure
; 4) Generate Config/QRCode
; 5) WEB/Server API 
; 6) Update cluster nodes state 



;; bash add-peer.sh wg0 94.176.238.220 10.7.0.11/32 fddd:2c4:2c4:2c4::11/128 dev2 51820




(peer->cluster p1 cluster1)

(node->cluster node1 cluster1)

(peer->node p1 cluster1)

(amount-peers-on-iface "wg0" node1)

(which-node? cluster1)

(peers-amount (nth (.nodes cluster1) 0))

(last-nodes cluster1 (nth (.nodes cluster1) 0))


(def node2 (->Node 3 [(->Interface "wg0" 
                                   {:inet "10.7.0.1/16" :inet6 "fddd:2c4:2c4:2c4::1/64"} 
                                   "3.3.4.5"
                                   "55890")]
                                   "1.1.1.1"  []))



(def node1 (->Node 3 [(->Interface "wg0" 
                                   {:inet "10.7.0.1/24" :inet6 "fddd:2c4:2c4:2c4::1/64"} 
                                   "3.3.4.5"
                                   "55890")]
                                   "1.1.1.1"  [


  (->Peer 0 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.2/32")
  (->Peer 1 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test2" "PSK-TEST2" "PUBLIC2" "PRIVATE2" "10.7.0.3/32")
  (->Peer 2 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test3" "PSK-TEST3" "PUBLIC3" "PRIVATE3" "10.7.0.4/32")
  (->Peer 2 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test3" "PSK-TEST3" "PUBLIC3" "PRIVATE3" "10.7.0.5/32")
  (->Peer 2 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test3" "PSK-TEST3" "PUBLIC3" "PRIVATE3" "10.7.0.6/32")
  (->Peer 2 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test3" "PSK-TEST3" "PUBLIC3" "PRIVATE3" "10.7.0.7/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.8/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.9/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.10/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.11/32")
  ;(->Peer 0 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.12/32")
  (->Peer 1 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test2" "PSK-TEST2" "PUBLIC2" "PRIVATE2" "10.7.0.13/32")
  (->Peer 2 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test3" "PSK-TEST3" "PUBLIC3" "PRIVATE3" "10.7.0.14/32")
  (->Peer 4 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.15/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test4" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.16/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.17/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.18/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.19/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.20/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.21/32")
  (->Peer 0 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.22/32")
  (->Peer 1 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test2" "PSK-TEST2" "PUBLIC2" "PRIVATE2" "10.7.0.23/32")
  (->Peer 2 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test3" "PSK-TEST3" "PUBLIC3" "PRIVATE3" "10.7.0.24/32")
  (->Peer 4 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.25/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test4" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.26/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.27/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.28/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.29/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.30/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.31/32")
  (->Peer 0 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.32/32")
  (->Peer 1 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test2" "PSK-TEST2" "PUBLIC2" "PRIVATE2" "10.7.0.33/32")
  (->Peer 2 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test3" "PSK-TEST3" "PUBLIC3" "PRIVATE3" "10.7.0.34/32")
  (->Peer 4 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.35/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test4" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.36/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.37/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.38/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.39/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.40/32")
  (->Peer 0 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.41/32")
  (->Peer 1 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test2" "PSK-TEST2" "PUBLIC2" "PRIVATE2" "10.7.0.42/32")
  (->Peer 2 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test3" "PSK-TEST3" "PUBLIC3" "PRIVATE3" "10.7.0.43/32")
  (->Peer 4 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.44/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test4" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.45/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.46/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.47/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.48/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.49/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.50/32")
  (->Peer 0 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.51/32")
  (->Peer 1 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test2" "PSK-TEST2" "PUBLIC2" "PRIVATE2" "10.7.0.52/32")
  (->Peer 2 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test3" "PSK-TEST3" "PUBLIC3" "PRIVATE3" "10.7.0.53/32")
  (->Peer 4 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.54/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test4" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.55/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.56/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.57/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.58/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.59/32")
  (->Peer 0 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.60/32")
  (->Peer 1 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test2" "PSK-TEST2" "PUBLIC2" "PRIVATE2" "10.7.0.61/32")
  (->Peer 2 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test3" "PSK-TEST3" "PUBLIC3" "PRIVATE3" "10.7.0.62/32")
  (->Peer 4 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.63/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test4" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.64/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.65/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.66/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.67/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.68/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.69/32")
  (->Peer 0 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.70/32")
  (->Peer 1 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test2" "PSK-TEST2" "PUBLIC2" "PRIVATE2" "10.7.0.71/32")
  (->Peer 2 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test3" "PSK-TEST3" "PUBLIC3" "PRIVATE3" "10.7.0.72/32")
  (->Peer 4 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.73/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test4" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.74/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.75/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.76/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.77/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.78/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.79/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.80/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.81/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.82/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.83/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.84/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.85/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.86/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.87/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.88/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.89/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.90/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.91/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.92/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.93/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.94/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.95/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.96/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.97/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.98/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.99/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.100/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.101/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.102/32")
  (->Peer 1 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test2" "PSK-TEST2" "PUBLIC2" "PRIVATE2" "10.7.0.103/32")
  (->Peer 2 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test3" "PSK-TEST3" "PUBLIC3" "PRIVATE3" "10.7.0.104/32")
  (->Peer 4 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.105/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test4" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.106/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.107/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.108/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.109/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.110/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.111/32")
  (->Peer 0 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.112/32")
  (->Peer 1 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test2" "PSK-TEST2" "PUBLIC2" "PRIVATE2" "10.7.0.113/32")
  (->Peer 2 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test3" "PSK-TEST3" "PUBLIC3" "PRIVATE3" "10.7.0.114/32")
  (->Peer 4 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.115/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test4" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.116/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.117/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.118/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.119/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.120/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.121/32")
  (->Peer 0 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.122/32")
  (->Peer 1 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test2" "PSK-TEST2" "PUBLIC2" "PRIVATE2" "10.7.0.123/32")
  (->Peer 2 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test3" "PSK-TEST3" "PUBLIC3" "PRIVATE3" "10.7.0.124/32")
  (->Peer 4 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.125/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test4" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.126/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.127/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.128/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.129/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.130/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.131/32")
  (->Peer 0 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.132/32")
  (->Peer 1 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test2" "PSK-TEST2" "PUBLIC2" "PRIVATE2" "10.7.0.133/32")
  (->Peer 2 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test3" "PSK-TEST3" "PUBLIC3" "PRIVATE3" "10.7.0.134/32")
  (->Peer 4 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.135/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test4" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.136/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.137/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.138/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.139/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.140/32")
  (->Peer 0 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.141/32")
  (->Peer 1 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test2" "PSK-TEST2" "PUBLIC2" "PRIVATE2" "10.7.0.142/32")
  (->Peer 2 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test3" "PSK-TEST3" "PUBLIC3" "PRIVATE3" "10.7.0.143/32")
  (->Peer 4 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.144/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test4" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.145/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.146/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.147/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.148/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.149/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.150/32")
  (->Peer 0 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.151/32")
  (->Peer 1 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test2" "PSK-TEST2" "PUBLIC2" "PRIVATE2" "10.7.0.152/32")
  (->Peer 2 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test3" "PSK-TEST3" "PUBLIC3" "PRIVATE3" "10.7.0.153/32")
  (->Peer 4 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.154/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test4" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.155/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.156/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.157/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.158/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.159/32")
  (->Peer 0 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.160/32")
  (->Peer 1 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test2" "PSK-TEST2" "PUBLIC2" "PRIVATE2" "10.7.0.161/32")
  (->Peer 2 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test3" "PSK-TEST3" "PUBLIC3" "PRIVATE3" "10.7.0.162/32")
  (->Peer 4 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.163/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test4" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.164/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.165/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.166/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.167/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.168/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.169/32")
  (->Peer 0 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.170/32")
  (->Peer 1 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test2" "PSK-TEST2" "PUBLIC2" "PRIVATE2" "10.7.0.171/32")
  (->Peer 2 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test3" "PSK-TEST3" "PUBLIC3" "PRIVATE3" "10.7.0.172/32")
  (->Peer 4 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.173/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test4" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.174/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.175/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.176/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.177/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.178/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.179/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.180/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.181/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.182/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.183/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.184/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.185/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.186/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.187/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.188/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.189/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.190/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.191/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.192/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.193/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.194/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.195/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.196/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.197/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.198/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.199/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.200/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.201/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.202/32")
  (->Peer 1 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test2" "PSK-TEST2" "PUBLIC2" "PRIVATE2" "10.7.0.203/32")
  (->Peer 2 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test3" "PSK-TEST3" "PUBLIC3" "PRIVATE3" "10.7.0.204/32")
  (->Peer 4 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.205/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test4" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.206/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.207/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.208/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.209/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.210/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.211/32")
  (->Peer 0 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.212/32")
  (->Peer 1 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test2" "PSK-TEST2" "PUBLIC2" "PRIVATE2" "10.7.0.213/32")
  (->Peer 2 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test3" "PSK-TEST3" "PUBLIC3" "PRIVATE3" "10.7.0.214/32")
  (->Peer 4 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.215/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test4" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.216/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.217/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.218/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.219/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.220/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.221/32")
  (->Peer 0 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.222/32")
  (->Peer 1 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test2" "PSK-TEST2" "PUBLIC2" "PRIVATE2" "10.7.0.223/32")
  (->Peer 2 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test3" "PSK-TEST3" "PUBLIC3" "PRIVATE3" "10.7.0.224/32")
  (->Peer 4 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.225/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test4" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.226/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.227/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.228/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.229/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.230/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.231/32")
  (->Peer 0 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.232/32")
  (->Peer 1 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test2" "PSK-TEST2" "PUBLIC2" "PRIVATE2" "10.7.0.233/32")
  (->Peer 2 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test3" "PSK-TEST3" "PUBLIC3" "PRIVATE3" "10.7.0.234/32")
  (->Peer 4 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.235/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test4" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.236/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.237/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.238/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.239/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.240/32")
  (->Peer 0 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.241/32")
  (->Peer 1 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test2" "PSK-TEST2" "PUBLIC2" "PRIVATE2" "10.7.0.242/32")
  (->Peer 2 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test3" "PSK-TEST3" "PUBLIC3" "PRIVATE3" "10.7.0.243/32")
  (->Peer 4 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.244/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test4" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.245/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.246/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.247/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.248/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.249/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test5" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.250/32")
  (->Peer 0 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.251/32")
  (->Peer 1 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test2" "PSK-TEST2" "PUBLIC2" "PRIVATE2" "10.7.0.252/32")
  (->Peer 2 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test3" "PSK-TEST3" "PUBLIC3" "PRIVATE3" "10.7.0.253/32")
  (->Peer 4 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test1" "PSK-TEST1" "PUBLIC1" "PRIVATE1" "10.7.0.254/32")
  (->Peer 3 (->Interface  "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c2::1/64"} "1.2.3.4" "55890") "test4" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.7.0.255/32")

  ]))







(def p1 (->Peer 3 (->Interface "wg0" {:inet "10.7.0.1/24", :inet6 "fddd:2c4:2c4:2c4::1/64"} "1.2.3.4:55890") "test4" "PSK-TEST4" "PUBLIC4" "PRIVATE4" "10.0.0.4/32"))

(println cluster)


(defn interface [] )

(defn endpoint [])

(defn allowed-ips [])

(defn name [])

(defn port [])



(defn peer->config [])

(defn qrcode [])





(def fake-peer 
  {:name "dev2",
    :psk "vc0Ysfpv3TU2VPv4G/7OuGEfuuq/BMSt8WaZJ30z8Qs=",
   :public-key "PUBLICKEY=",
   :private_key "PRIVATEKEY"
     
       :allowed-ips ["10.7.0.11/32" "fddd:2c4:2c4:2c4::11/128"],
       :interface "wg0",
       :endpoint "94.176.238.220:51820",
        
       :dns "1.1.1.1, 1.0.0.1"})



(apply ->Peer (->Interface (:interface fake-peer) (:endpoint fake-peer))
              (vals (dissoc fake-peer :interface :endpoint :dns))) 

(->Peer (:interface fake-peer)
        (:name fake-peer)
        (:public-key fake-peer)
        (:psk fake-peer)
        (:allowed-ips fake-peer)
        (:endpoint fake-peer)
        (:dns fake-peer))







(defn peer [Peer]
  (shell/sh wg set (:interface Peer )
  )



(defn run-bash-script [script-path args]
  (let [{:keys [out err]} (sh "bash" script-path args)]
    (str out)))

(defn login-handler []
  (go
    (let [result (run-bash-script (:bash-script (httpkit/get-options)) args)]
      {:status 200 :body result})))

(defroutes app
  (GET "/" [] "<h1>Hello World</h1>")
  (GET "/peer" [] peer)
  (route/not-found "<h1>Page not found</h1>"))

(defn start-server [options]
  (httpkit/run-server app options))

(start-server app {:port 8080})