(ns wgctrl.cluster.model-test
  (:require [clojure.test :refer :all]
            [wgctrl.cluster.model :refer :all]
            [wgctrl.cluster.model :refer [interface! peer!]]
            [wgctrl.cluster.transforms :refer [peer->interface]]))

(deftest peer!-test 
	(let [p (peer! {:peer "peer" :psk "psk" :ip "ip"})]
		(is (instance? wgctrl.cluster.model.Peer  p))
		(is (or (not(nil? (.peer p)))
		        (not(nil? (.psk p))))
		        (not(nil? (.allowed-ips p)))))
	(let [p (peer! ["peer" "psk" "ip" "broken"])]
		(is (instance? wgctrl.cluster.model.Peer p))
		(is (or (nil? (.peer p))
		        (nil? (.psk p))
		        (nil? (.allowed-ips p))))))

(def i (interface!  {:name "wg0" 
	          :subnet {:inet "192.168.0.1/24"} 
	          :endpoint {:inet "127.0.0.1"}  
	          :port 55555
	          :public-key "PUBLICKEY"}))

	          (-> i .subnet :inet)	           



(deftest interface!-test 
	(let [iface (interface!  {:name "wg0" 
	          :subnet {:inet "192.168.0.1/24"} 
	          :endpoint {:inet "127.0.0.1"}  
	          :port 55555
	          :public-key "PUBLICKEY"})]
	(is (instance? wgctrl.cluster.model.Interface iface))
	(is (or (not(nil? (.name iface)))
		    (not(nil? (.subnet iface)))
		    (not(nil? (.endpoint iface)))
		    (not(nil? (.port iface))
		    (not(nil? (.public-key iface))))))
	))

(def mock-node-data {:uuid "79d2843f-15b2-4484-8612-c570a8bdfe24", 
	:hostname "dev1.vpn.dev", 
	:default-interface "ens3", 
	:interfaces [{:name "wg0", 
	              :subnet {:inet "10.7.0.1/24,", :inet6 "fddd:2c4:2c4:2c4::1/64"}, 
	              :port "51820", 
	              :public-key "DXn0oXV5/5fCtgKlf9VjqKkECX/wibquJYX6/9wCASM=", 
	              :endpoint {:inet "94.16.2.20", :inet6 "2a02:74e0:5ea0:eddc::1"}}]})


(deftest node!-test 
	(let [node (node! mock-node-data)]
	(is (instance? wgctrl.cluster.model.Node node))
	(is (or (not(nil? (.uuid node)))
		    (not(nil? (.hostname node)))
		    (not(nil? (.interfaces node)))
		    (not(nil? (.status node))
		    (not(nil? (.weight node))))))
	))


	
