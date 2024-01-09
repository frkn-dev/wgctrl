(ns wgctrl.core
  (:gen-class)
  (:require     [clojure.tools.logging :as log]
    [wgctrl.config :refer [config]]
    [wgctrl.nrepl :refer [nrepl-server]]
    [wgctrl.http.api :refer [api-server]]
    [mount.core :refer [start stop only]]))


(defn -main []
  
  
  (start)
  
  (log/info (str "WGCTRL is running..."
                 "\nUsing next configuration ->> " config)
                 "\nListening api port: " (:api-port config)
                 "\nState restore -> "))
