(ns wgctrl.core
  (:gen-class)
  (:require     
    [clojure.tools.logging :refer [info]]
    [mount.core :as mount]
    [clojure.java.jdbc :as jdbc]
    [wgctrl.logging :refer [with-logging-status]]
    [wgctrl.config :refer [config]]
    [wgctrl.http.api :refer [api-server]]
    [wgctrl.cluster.nodes :refer [nodes]]
    [wgctrl.cluster.balancer :refer [balancers]]
    [wgctrl.db :as db]
    [wgctrl.nrepl :refer [nrepl-server]]))

    


(defn -main []
  
  (with-logging-status)
  (mount/start #'wgctrl.config/config
    #'wgctrl.cluster.nodes/nodes
    #'wgctrl.cluster.balancer/balancers
    #'wgctrl.db/db
    #'wgctrl.http.api/api-server
    #'wgctrl.nrepl/nrepl-server)   
  
  (info (str "WGCTRL is running..."
          "\nUsing next configuration ->> " config)
    "\nListening api port: " (:api-port config))
  
  (if (db/table-exist? db/table-name)
    (do (info "Table already exist, skip") nil)
    (do (info "Creating table") 
      (jdbc/db-do-commands db/db db/table-query)))
  
  (do (info "Updating DB")
    (db/peers->db nodes))
  )

