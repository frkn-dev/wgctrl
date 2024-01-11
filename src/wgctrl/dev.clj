(ns wgctrl.dev 
  (:require [mount.core :as mount :refer [defstate]]
    [clojure.core.async :refer [chan timeout go-loop <! >! >!! <!!]]
    [clojure.tools.namespace.repl :as tn]
    [clojure.java.shell :refer [sh]]
    [clojure.string :as str]
    [mount.tools.graph :refer [states-with-deps]]
    [wgctrl.logging :refer [with-logging-status]]
    [clojure.java.jdbc :as jdbc]
    [clojure.edn :as edn]
    [clojure.tools.logging :refer [info]]
    [clojure.spec.alpha :as s]
    [clojure.test.check.generators :as gen]
    
    
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
  (mount/start #'wgctrl.config/config)
  
  (-> config)
  )
