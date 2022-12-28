(ns wgctrl.http.handlers
  (:require [clojure.string :as str]
            [cheshire.core :as json]
            [wgctrl.cluster.model :as m]
            [wgctrl.cluster.transforms :as t]
            [wgctrl.cluster.selectors :as s]
            [wgctrl.cluster.ssh :as ssh]
            [wgctrl.cluster.keys :as keys]
            [wgctrl.cluster.stat :as stat]
            [wgctrl.cluster.utils :as utils])
  (:use [clojure.walk :only [keywordize-keys]]))

(defn peer [req]
  (let [location (or (-> req :params keywordize-keys :location) "dev")
        node (s/node-with-min-peers @(.nodes m/cluster) location)
        interface (s/interface-with-min-peers node)]
        (println location "TT")
        (if (or (empty? node)
                (nil? interface))
            {:body (json/generate-string {:code 10
                                          :err "Can't create peer" 
                                          :message (str "Node or Interface for " location " not found")})
            :code 200
            :headers {"Content-Type" "application/json; charset=utf-8"    
                      "Access-Control-Allow-Origin" "*"}}



            (let [keys (keys/generate (.key interface))
                ip (utils/addr! interface)]
      
              (let [{:keys [err exit]} (ssh/peer! keys interface ip)]
                (if (= 0 exit)
                  (do (t/peer->interface (m/peer! [(:client-pubkey keys)
                                                   (:client-psk keys)
                                                   (str ip "/32")]) interface)
        
                           {:code 200
                            :headers {"Content-Type" "application/json; charset=utf-8"
                                      "Access-Control-Allow-Origin" "*"}
                            :body (json/generate-string 
                                {:interface {:address (str ip "/24")
                                             :key (:client-key keys)
                                             :dns (.dns node)}
                                 :peer {:pubkey (:server-pubkey keys)
                                        :psk  (:client-psk keys)
                                        :allowed_ips "0.0.0.0/0"
                                        :endpoint (str (-> interface .endpoint :inet) ":"
                                                       (-> interface .port))}})})
                       
                       {:body (json/generate-string {:code 10 :err "Can't create peer" :message err})
                        :code 200
                        :headers {"Content-Type" "application/json; charset=utf-8" 
                                  "Access-Control-Allow-Origin" "*"}}))))))

(defn stat [request]
  (let [stat (stat/cluster @(.nodes m/cluster))]
    (println "STAT " (json/generate-string (conj {:uuid (.uuid m/cluster)} {:nodes stat})))
    {:body (json/generate-string (conj {:uuid (.uuid m/cluster)} {:nodes stat}))
     :code 200
     :headers {"Content-Type" "application/json; charset=utf-8"
               "Access-Control-Allow-Origin" "*"}}))

(defn locations [request]
  (let [locations (s/available-locations @(.nodes m/cluster))]
    (println (vec locations))
    {:body (json/generate-string {:locations (vec locations)})
     :code 200
     :headers {"Content-Type" "application/json; charset=utf-8"
               "Access-Control-Allow-Origin" "*"}}))

