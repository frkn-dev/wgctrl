(ns wgctrl.core
  (:gen-class)
  (:require [wgctrl.http.routes :as routes]
            [org.httpkit.server :as httpkit]
            [compojure.core :refer :all]
            [compojure.route :refer [not-found]]
            [wgctrl.cluster.model :as m]
            [wgctrl.cluster.transforms :as t]
            [wgctrl.cluster.selectors :as s]
            [wgctrl.http.handlers :as handlers]
            [wgctrl.cluster.ssh :as ssh]
            [clojure.core.async :as async :refer [go go-loop <! >! chan]])
  (:use [clojure.walk :only [keywordize-keys]]))


(defonce server (atom nil))

(def config {:nodes [{:address "root@94.176.238.220" :location "dev" :dns "1.1.1.1"}]
             :port 8080})

(defn -main []
  (println "WGCTRL is running...")

  (if (empty? @(.nodes m/cluster))
      (reduce (fn [cluster' data'] 
                  (t/node->cluster (m/node! (ssh/node-reg-data data')) cluster')) 
                   m/cluster (:nodes config)))

  (println "Listening port: 8080")
  (reset! server (httpkit/run-server #'routes/app {:port 8080})))


