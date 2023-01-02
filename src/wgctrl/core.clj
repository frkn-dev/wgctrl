(ns wgctrl.core
  (:gen-class)
  (:require [nrepl.server :refer [start-server]]
            [clojure.tools.logging :as log]
            [org.httpkit.server :as httpkit]
            [wgctrl.http.routes :as routes]
            [wgctrl.cluster.state :as state]
            [wgctrl.cluster.balancer :as b]))


(defonce api-server (atom nil))
(defonce nrepl-server (atom nil))

(def config {:nrepl {:bind "127.0.0.1" :port 7888}
             :api-port 8080
             :nodes [{:address "root@94.176.238.220"
                      :location {:code "dev" :name "🏴‍☠️ Development"}
                      :dns "1.1.1.1, 1.0.0.1"
                      :weight 1}]})

(defn stop-api-server []
  (when-not (nil? @api-server)
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    ;; :timeout is optional, when no timeout, stop immediately

    (@api-server :timeout 100)
    (reset! api-server nil)))


(defn -main []
  (log/info "WGCTRL is running...")
  (log/info (str "Using next configuration ->> " config))

  (state/restore-state config)
  
  (log/info "Listening nrepl port: " (:nrepl config))
  (reset! nrepl-server (start-server (:nrepl config)))

  (log/info "Listening api port: " (:api-port config))
  (reset! api-server (httpkit/run-server #'routes/app {:port (:api-port config)})))




