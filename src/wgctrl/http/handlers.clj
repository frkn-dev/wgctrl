(ns wgctrl.http.handlers
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [clojure.core.async :refer [<!!]]
            [wgctrl.cluster.model :as m]
            [wgctrl.cluster.transforms :as t]
            [wgctrl.cluster.selectors :as s]
            [wgctrl.cluster.ssh :as ssh]
            [wgctrl.cluster.stat :as stat]
            [wgctrl.cluster.utils :as utils]
            [wgctrl.cluster.state :as state])
  (:use [clojure.walk :only [keywordize-keys]]))

(defn peer [req]
  (let [params (-> req :params keywordize-keys)
        location (keyword (or (-> params :location) "all"))
        pubkey (-> params :pubkey)]

    (if (nil? (location @(.balancer state/cluster)))
      {:code 200
       :headers {"Content-Type" "application/json; charset=utf-8"
                 "Access-Control-Allow-Origin" "*"}
       :body (json/generate-string {:code 10
                                    :err "Can't create peer"
                                    :message (str "Node within location " location " not found")})}

      (let [node-uuid (:uuid (<!! (location @(.balancer state/cluster) )))
            node (s/node-by-uuid @(.nodes state/cluster) node-uuid)
            interface (s/interface-with-min-peers node)
            ip (utils/addr! interface)]
      
        (let [peer (ssh/peer! interface ip)]
          (t/peer->interface (m/peer! {:peer (:peer peer)  :ip (str ip "/32")}) interface)
          (log/info (str "GET /peer?location=" location " - "
                      peer  " "
                      (str ip "/32")))
          {:code 200
           :headers {"Content-Type" "application/json; charset=utf-8"
                     "Access-Control-Allow-Origin" "*"}
           :body (json/generate-string
                   {:iface {:address (str ip "/24")
                            :key  (:private peer)
                            :dns (.dns node)}
                    :peer {:pubkey (.key interface) 
                           :psk (.psk interface)
                           :allowed_ips "0.0.0.0/0"
                           :endpoint (str (-> interface .endpoint) ":"
                                       (-> interface .port))}})})))))

(defn stat [request]
  (let [stat (stat/cluster @(.nodes state/cluster))]
    (log/info (str "GET /stat " request " - " stat))
    {:body (json/generate-string (conj {:uuid (.uuid state/cluster)} {:nodes stat}))
     :code 200
     :headers {"Content-Type" "application/json; charset=utf-8"
               "Access-Control-Allow-Origin" "*"}}))


(defn locations [request]
  (let [locations (s/available-locations @(.nodes state/cluster))]
    (log/info (str "GET /locations " request " - " locations))
    {:body (json/generate-string (vec (reverse (sort-by :code locations))))
     :code 200
     :headers {"Content-Type" "application/json; charset=utf-8"
               "Access-Control-Allow-Origin" "*"}}))

