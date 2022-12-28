(ns wgctrl.core
  (:gen-class)
  (:require [nrepl.server :refer [start-server stop-server]]
            [org.httpkit.server :as httpkit]
            [compojure.core :refer :all]
            [compojure.route :refer [not-found]]
            [wgctrl.http.routes :as routes]
            [wgctrl.cluster.model :as m]
            [wgctrl.cluster.transforms :as t]
            [wgctrl.cluster.selectors :as s]
            [wgctrl.http.handlers :as handlers]
            [wgctrl.cluster.ssh :as ssh]
            [wgctrl.cluster.utils :as utils]
            [clojure.core.async :as async :refer [go go-loop <! >! chan]])
  (:use [clojure.walk :only [keywordize-keys]]))


(defonce api-server (atom nil))
(defonce nrepl-server (atom nil))

(def config {:nodes [{:address "root@94.176.238.220" :location {:code "dev" :name "🏴‍☠️ Development"} :dns "1.1.1.1"}]
             :port 8080})


(defn stop-api-server []
  (when-not (nil? @api-server)
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    ;; :timeout is optional, when no timeout, stop immediately
    (@api-server :timeout 100)
    (reset! api-server nil)))

(defn -main []
  (println "WGCTRL is running...")

  (if (empty? @(.nodes m/cluster))
      (reduce (fn [cluster' data'] 
                  (t/node->cluster (m/node! (ssh/node-reg-data data')) cluster')) 
                   m/cluster (:nodes config)))

  (println "Listening nrepl port: 7888")
  (reset! nrepl-server (start-server :bind "127.0.0.1" :port 7888))

  (println "Listening api port: 8080")
  (reset! api-server (httpkit/run-server #'routes/app {:port 8080})))

@(.peers (first @(.interfaces (first @(.nodes (-> m/cluster
  ))))))

;(stop-server)
;(-main)