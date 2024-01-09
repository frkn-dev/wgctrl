(ns wgctrl.dev 
  (:require [mount.core :refer [start stop only defstate]]
    [clojure.core.async :refer [chan timeout go-loop <! >! >!! <!!]]
    [clojure.tools.namespace.repl :refer [refresh]]
    [clojure.java.shell :refer [sh]]
    [clojure.string :as str]
    
    
    [wgctrl.config :refer [config]]
    [wgctrl.cluster.balancer :refer [balancers]]
    [wgctrl.cluster.ipcalc :as ip]
    [wgctrl.cluster.nodes :refer [nodes node!]]
    [wgctrl.cluster.peers :refer [addr!]]
    [wgctrl.nrepl :refer [nrepl-server]]
    [wgctrl.http.api :refer [api-server]]
    
    [wgctrl.ssh.common :as ssh]
    [wgctrl.ssh.nodes :as ssh-nodes]
    [wgctrl.ssh.peers :as ssh-peers]))


 (defn go []
    (start)
    :ready)

  (defn reset []
    (stop)
    (refresh :after 'dev/go))


(comment 
  
  (-> nodes first addr!)
  (ssh-peers/peer! (first nodes) "10.8.1.26")
  
  
  (node! (ssh-nodes/node-reg-data (-> config :nodes first )))
  
  (count (ssh-nodes/node-reg-data (-> config :nodes first )))
  
  (reset)
  
  (stop)
  (start)
  (start)
  (-> nodes)
  
  (-> config)
  

 )
