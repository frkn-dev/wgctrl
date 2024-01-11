(ns wgctrl.http.handlers
  (:require [clojure.string :as str]
    [clojure.tools.logging :as log]
    [cheshire.core :as json]
    [clojure.core.async :refer [<!!]]
    [wgctrl.cluster.balancer :refer [balancers]]
    [wgctrl.cluster.nodes :refer [nodes locations node-by-uuid]]
    [wgctrl.cluster.peers :refer [addr!]]
    [wgctrl.cluster.stat :as stat]
    [wgctrl.ssh.peers :as ssh]
    [wgctrl.db :as db])
  (:use [clojure.walk :only [keywordize-keys]]))


(defn locs [request]
  (let [locations' (locations nodes)]
    (log/info (str "GET /locations " request " - " locations'))
    {:body (json/generate-string (vec (reverse (sort-by :code locations'))))
     :code 200
     :headers {"Content-Type" "application/json; charset=utf-8"
               "Access-Control-Allow-Origin" "*"}}))


(defn peers-stat [request]
  (log/info (str "GET /peers/stat " request ))
  {:body (json/generate-string (map #(stat/peers-stat %) nodes))
   :code 200
   :headers {"Content-Type" "application/json; charset=utf-8"
             "Access-Control-Allow-Origin" "*"}})

(defn peers-amount [request]
  (log/info (str "GET /peers/amount " request ))
  {:body (json/generate-string (mapcat #(vector {:node (:uuid %) 
                                                 :hostname (:hostname %) 
                                                 :location (:location %) 
                                                 :type (:type %)
                                                 :peers (stat/peers-amount %)}) nodes))
   :code 200
   :headers {"Content-Type" "application/json; charset=utf-8"
             "Access-Control-Allow-Origin" "*"}})


(defn peers-live [request]
  (log/info (str "GET /peers/live " request ))
  (let [peers (map #(stat/peers-amount %) nodes)]
    {:body (json/generate-string  {:online (reduce + (map #(:live %) peers))
                                   :tx_gb (/ (reduce + (map #(parse-long (:tx %)) peers)) 1024 1024 1024)
                                   :rx_gb (/ (reduce + (map #(parse-long (:rx %)) peers)) 1024 1024 1024)
                                   })
     :code 200
     :headers {"Content-Type" "application/json; charset=utf-8"
               "Access-Control-Allow-Origin" "*"}}))

(defn peers-active [request]
  (log/info (str "GET /peers/active " request ))
  {:body (json/generate-string (map #(stat/peers-alive (stat/peers-dump %)) nodes))
   :code 200
   :headers {"Content-Type" "application/json; charset=utf-8"
             "Access-Control-Allow-Origin" "*"}})


(defn peer [req]
  (let [params (-> req :params keywordize-keys)
        location (keyword (or (:location params ) "all"))
        pubkey (-> params :pubkey)]

    (if (nil? (location balancers))
      {:code 200
       :headers {"Content-Type" "application/json; charset=utf-8"
                 "Access-Control-Allow-Origin" "*"}
       :body (json/generate-string {:code 10
                                    :err "Can't create peer"
                                    :message (str "Node within location " location " not found")})}

      (let [node-uuid (:uuid (<!! (location balancers)))
            node (node-by-uuid nodes node-uuid)
            ip (addr! node)]
        
        (if (nil? ip)
          {:code 200
           :headers {"Content-Type" "application/json; charset=utf-8"
                     "Access-Control-Allow-Origin" "*"}
           :body (json/generate-string {:code 10
                                        :err "Can't create peer"
                                        :message (str "Not enough IP addresses on Node")})}
          

          
          (let [peer (ssh/peer! node ip)]
            (do (log/info (str "--> Generating peer " (conj (dissoc peer :private) {:node-id (:uuid node)})))
              (db/peer->db (conj peer {:node-id (:uuid node)})))
            (log/info (str "GET /peer?location=" location " - " (dissoc peer :private) ))
          
            {:code 200
             :headers {"Content-Type" "application/json; charset=utf-8"
                       "Access-Control-Allow-Origin" "*"}
             :body (json/generate-string
                     {:iface {:address (str ip "/24")
                              :key  (:private peer)
                              :dns (:dns node)}
                      :peer {:pubkey (-> node :interface :public-key) 
                             :psk (-> node :interface :psk) 
                             :allowed_ips "0.0.0.0/0"
                             :endpoint (str (-> node :endpoint)  ":"
                                         (-> node :interface :port))}})}))))))



