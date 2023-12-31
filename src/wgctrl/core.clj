(ns wgctrl.core
  (:gen-class)
  (:require [nrepl.server :refer [start-server]]
    [clojure.tools.logging :as log]
    [org.httpkit.server :as httpkit]
    [wgctrl.http.routes :as routes]
    [wgctrl.cluster.state :as state]
    [wgctrl.cluster.utils :as utils]
    [wgctrl.cluster.balancer :as b]
    
    [clojure.string :as str]
    [wgctrl.cluster.checks :as c]
    [clojure.core.async :refer [chan timeout go-loop <! >! >!! <!!]]
    [wgctrl.cluster.transforms :as t]
    [wgctrl.cluster.selectors :as s]
    [wgctrl.cluster.ipcalc :as ip]
    [wgctrl.cluster.ssh :as ssh]
    [wgctrl.cluster.model :as m]
    [clojure.java.shell :refer [sh]])
  (:use [clojure.walk :only [keywordize-keys]]))


(defonce api-server (atom nil))
(defonce nrepl-server (atom nil))
(defonce config (atom nil))


(defn stop-api-server []
  (when-not (nil? @api-server)
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    ;; :timeout is optional, when no timeout, stop immediately

    (@api-server :timeout 100)
    (reset! api-server nil)))

(defn config-loop []
  (loop []
    
    (reset! config (utils/load-edn "./config.edn"))
    (Thread/sleep 1000) ; Sleep for 1 second
    (recur)))


(defn -main []
  (log/info "WGCTRL is running...")
  
  (def bg-config-future (future (config-loop)))
  
  

  (log/info (str "Using next configuration ->> " (deref config)))
  (reset! config (utils/load-edn "./config.edn")) 
  
  (state/restore-state config)
  
  (log/info "Listening nrepl port: " (:nrepl @config))
  (reset! nrepl-server (start-server (:nrepl @config)))

  (log/info "Listening api port: " (:api-port @config))
  (reset! api-server (httpkit/run-server #'routes/app {:port (:api-port @config)})))


(-main)

(comment 
  
  (-> state/cluster .nodes)
  
  (def n0 (-> @(-> state/cluster .nodes) first))
  (-> n0)
  
  (def wg0 (first @(.interfaces n0)))
  (-> wg0)
)


