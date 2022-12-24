(ns wgctrl.core
  (:gen-class)
  (:require [wgctrl.cluster.model :as m]
            [wgctrl.cluster.transforms :as t]
            [wgctrl.cluster.ssh :as ssh]
            [wgctrl.http.http :as http])
  (:use [clojure.walk :only [keywordize-keys]]))

(def config {:nodes [{:address "root@94.176.238.220" :location "dev" :dns "1.1.1.1"}]})

(defn -main []

  (reset! (.nodes m/cluster) [])
  (doall (map #(t/node->cluster (m/node! (ssh/node-reg-data %)) m/cluster) (:nodes config)))

  (def server (atom nil))
  (http/stop-server server)
  (reset! server (http/start-server {:port 8080})))

(-main)


