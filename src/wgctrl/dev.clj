(ns wgctrl.dev 
  (:require [mount.core :as mount :refer [defstate]]
    [clojure.core.async :refer [chan timeout go-loop <! >! >!! <!!]]
    [clojure.tools.namespace.repl :as tn]
    [clojure.java.shell :refer [sh]]
    [clojure.string :as str]
    [mount.tools.graph :refer [states-with-deps]]
    [wgctrl.logging :refer [with-logging-status]]
    [clojure.java.jdbc :as jdbc]
    
    
    [wgctrl.config :refer [config]]
    [wgctrl.cluster.balancer :refer [balancers]]
    [wgctrl.cluster.ipcalc :as ip]
    [wgctrl.cluster.nodes :refer [nodes node!]]
    [wgctrl.cluster.peers :refer [addr!]]
    [wgctrl.nrepl :refer [nrepl-server]]
    [wgctrl.http.api :refer [api-server]]
    
    [wgctrl.ssh.common :as ssh]
    [wgctrl.db :as db]

    [wgctrl.ssh.nodes :as ssh-nodes]
    [wgctrl.ssh.peers :as ssh-peers]))



(defn start []
  (with-logging-status)
  (mount/start #'wgctrl.config/config
               #'wgctrl.cluster.nodes/nodes
               #'wgctrl.cluster.balancer/balancers
               #'wgctrl.db/db
               #'wgctrl.http.api/api-server
               #'wgctrl.nrepl/nrepl-server))

(defn stop []
  (mount/stop))

(defn refresh []
  (stop)
  (tn/refresh))

(defn refresh-all []
  (stop)
  (tn/refresh-all))

(defn go
  "starts all states defined by defstate"
  []
  (start)
  :ready)

(defn reset
  "stops all states defined by defstate, reloads modified source files, and restarts the states"
  []
  (stop)
  (tn/refresh :after 'wgctrl.dev/go))

(mount/in-clj-mode)









(comment 
   (start)
   (reset)
  
  
  
  (peers->db nodes)
  
  (-> nodes)
  (-> balancers)
  
  ()
  
 (filter #(empty? (jdbc/find-by-keys db/db :peers {:peer (:peer %)})) (ssh-peers/peers (first nodes)))
  
  (count (jdbc/query db/db "Select * from peers"))
  
  (map #(jdbc/find-by-keys db/db :peers {:peer %}) (ssh-peers/peers (first nodes)))
  
  (filter odd? [1 2 3 4 5 6])
  
  (ssh-peers/peers (first nodes))
  
(first (jdbc/query db/db "SELECT * from peers"))
  
  {:node-id "2ab657cd-1301-4699-aeb4-785d4cb1fc1a", :peer "mmJ5z1W7pjwZujEtok3YKFNAQGkOE8Rg7uHOq3H3NV0=", :ip "10.8.1.57/32"}
  
  (jdbc/find-by-keys db/db :peers {:peer "mmJ5z1W7pjwZujEtok3YKFNAQGkOE8Rg7uHOq3H3NV0="})
  
  (-> db/db)
  
  (peers-state nodes )
  
  (-> nodes)
  
  (mapcat #(remove peer-loaded? (ssh-peers/peers %)) nodes)
  
  
  (db/bulk-peer-insert db/db  (mapcat #(->> (ssh-peers/peers %) 
                (remove peer-loaded?))  nodes ))
  
 
   (remove #(peer-loaded? %) (ssh-peers/peers (first nodes)))
 
  (peer-loaded? "mmEU2aFFHu7IJTa9e+94J8mLSrb0qRl3xRIUZXaGGTA=")
  
  (remove even? [1 2 3 4 5 6 7 8])
  
  (jdbc/query db/db "Select * from peers")
  
  {:peer "mmEU2aFFHu7IJTa9e+94J8mLSrb0qRl3xRIUZXaGGTA=", :id 1, :node_id "2ab657cd-1301-4699-aeb4-785d4cb1fc1a", :created_at "1704848147805", :ip "10.8.1.122/32"}
 
(first   (ssh-peers/peers (first nodes)))
  
  {:node-id "2ab657cd-1301-4699-aeb4-785d4cb1fc1a", :peer "13K4n9NXX+nwaI7AKFs81Yzh+fS27cN1M4/llU1ISFk=", :ip "10.8.1.94/32"}

 )
