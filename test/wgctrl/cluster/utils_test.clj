(ns wgctrl.cluster.utils-test
  (:require [clojure.test :refer :all]
            [wgctrl.cluster.utils :refer :all]
            [wgctrl.cluster.model :refer [interface! peer!]]
            [wgctrl.cluster.transforms :refer [peer->interface]]))


(def peers1 [{:peer "peer1" :psk "psk1" :ip "ip1"}
             {:peer "peer2" :psk "psk2"  :ip "ip2"}])


(def peers2 [{:peer "peer1" :psk "psk1" :ip "ip1"}
             {:peer "peer2" :psk "psk2" :ip "ip2"}
             {:peer "peer3" :psk "psk3" :ip "ip3"}
             {:peer "peer4" :psk "psk4" :ip "ip4"}
             {:peer "peer5" :psk "psk5" :ip "ip5"}
             {:peer "peer6" :psk "psk6" :ip "ip6"}])

(defn mock-iface [peers]
  (let [iface (interface!  {:name "wg0" 
	          :subnet {:inet "192.168.0.1/24"} 
	          :endpoint {:inet "127.0.0.1"}  
	          :port 55555
	          :public-key "KEY"})]
  (for [p peers]
    (peer->interface (peer! p) iface))))


(deftest test-interface-size
  (let [iface (first (mock-iface peers1))]
    (is (= 2 (interface-size iface))))
  (let [iface (first (mock-iface peers2))]
    (is (= 6 (interface-size iface)))))

