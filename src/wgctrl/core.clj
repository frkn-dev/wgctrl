(ns wgctrl.core
  (:gen-class)
  (:require [wgctrl.cluster.model :as m]
            [wgctrl.cluster.transforms :as t]
            [wgctrl.cluster.ssh :as ssh]
            [wgctrl.http.main :as http]
            [wgctrl.cluster.selectors :as s]
            [wgctrl.cluster.keys :as keys]
            [wgctrl.cluster.utils :as utils])
  (:use [clojure.walk :only [keywordize-keys]]))


(def config {:nodes [{:address "root@94.176.238.220" :location "dev" :dns "1.1.1.1"}
                     ]})


(def d1 {:location "dv" :dns "1.1.1.1" :uuid "35d200ae-4a28-9592-201dc6358714", :default-interface "ens3", :interfaces 
  [{:name "wg0", :subnet {:inet "10.7.0.1/16,", :inet6 "fddd:2c4:2c4:2c4::1/64"}, :port "51777", :endpoint {:inet "45.91.8.110", :inet6 "2a0a:2b41:1:e96b::"}} {:name "wg1", :subnet {:inet "10.10.0.1/16,", :inet6 "fddd:2c4:2c4:2c5::1/64"}, :port "51887", :endpoint {:inet "45.91.8.110", :inet6 "2a0a:2b41:1:e96b::"}}]})
(def d2 {:location "ru" :dns "1.1.1.1" :uuid "35d200ae-8234-4a28-201dc6358714", :default-interface "ens3", :interfaces 
  [{:name "wg0", :subnet {:inet "10.8.0.1/16,", :inet6 "fddd:2c4:2c4:2c5::1/64"}, :port "51777", :endpoint {:inet "46.91.9.112", :inet6 "2a0a:2b41:1:e96b::"}} {:name "wg1", :subnet {:inet "10.11.0.1/16,", :inet6 "fddd:2c4:2c4:2c6::1/64"}, :port "51887", :endpoint {:inet "46.91.9.112", :inet6 "2a0a:2b41:1:e96b::"}}]})
(def d3 {:location "lt" :dns "1.1.1.1" :uuid "35d200ae-8234-9592-201dc6358714", :default-interface "ens3", :interfaces 
  [{:name "wg0", :subnet {:inet "10.9.0.1/16,", :inet6 "fddd:2c4:2c4:2c6::1/64"}, :port "51777", :endpoint {:inet "47.91.7.115", :inet6 "2a0a:2b41:1:e96b::"}} {:name "wg1", :subnet {:inet "10.12.0.1/16,", :inet6 "fddd:2c4:2c4:2c7::1/64"}, :port "51887", :endpoint {:inet "47.91.7.115", :inet6 "2a0a:2b41:1:e96b::"}}]})


(def i1 (m/interface! (first (:interfaces d1))))
(def i2 (m/interface! (first (:interfaces d2))))
(def i3 (m/interface! (first (:interfaces d3))))

(def i1' (m/interface! (last (:interfaces d1))))
(def i2' (m/interface! (last (:interfaces d2))))
(def i3' (m/interface! (last (:interfaces d3))))

(def p1 (m/peer! [(utils/uuid) "p1" "psk1" "public1" "private1" "10.7.0.1/32"]))
(def p2 (m/peer! [(utils/uuid) "p2" "psk2" "public2" "private2" "10.7.0.2/32"]))
(def p3 (m/peer! [(utils/uuid) "p3" "psk3" "public3" "private3" "10.7.0.3/32"]))
(def p4 (m/peer! [(utils/uuid) "p4" "psk4" "public4" "private4" "10.7.0.4/32"]))
(def p5 (m/peer! [(utils/uuid) "p5" "psk5" "public5" "private5" "10.7.0.5/32"]))

(def n1 (m/node! d1))
(def n2 (m/node! d2))
(def n3 (m/node! d3))



(defn -main []
   
       (reset! (.nodes m/cluster) [])
       (doall (map #(t/node->cluster (m/node! (ssh/node-reg-data %)) m/cluster) (:nodes config)))
  
       (defonce server (atom nil))
       (http/stop-server server)
       (reset! server (http/start-server {:port 8080})))


(-main)


(keys/generate-client (first @(.interfaces (first @(.nodes (-> m/cluster))))))

(s/node-with-min-peers @(.nodes m/cluster) "dev")

(s/nodes-by-location "dev" @(.nodes m/cluster))

(utils/addr! 
  (s/interface-with-min-peers (s/node-with-min-peers @(.nodes m/cluster) "dev")))

