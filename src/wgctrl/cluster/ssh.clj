(ns wgctrl.cluster.ssh
	(:gen-class)
	(:require [clojure.edn :as edn]
		        [clojure.java.shell :as shell]))

(defn node-reg-data 
  "Gets data from node"
  [node]
  (-> (shell/sh "ssh" (:address node) "cat" "/root/.wg-node") 
       :out
       (edn/read-string)
       (conj {:location (:location node)})
       (conj {:dns (:dns node)})))

;;(defn peer! [cluster]
;;  "FIXME"
;;  (let [n (c/which-node? cluster)
;;        i (c/which-interface? n)]
;;
;;        (let [endpoint (.endpoint i)
;;              port (.port i)
;;              iface (.name i)
;;              ipv4 (str (c/addr! n) "/32")
;;              ipv6 "fddd:2c4:2c4:2c4::2/128"
;;              name (u/uuid)
;;              path "scripts/add-peer.sh"]
;;              (println (str "root@" endpoint) path iface endpoint ipv4 ipv6 name port)
;;         ; (shell/sh "ssh" (str "root@" endpoint) path iface endpoint ipv4 ipv6 name port)
;;         )))
;;


