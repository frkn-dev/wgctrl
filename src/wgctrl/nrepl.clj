(ns wgctrl.nrepl 
  (:require  [mount.core :refer [defstate]]
    [nrepl.server :refer [start-server stop-server]]
    [wgctrl.config :refer [config]]))

(defstate nrepl-server 
  :start (start-server (:nrepl config)) 
  :stop (stop-server nrepl-server))

