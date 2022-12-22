(ns wgctrl.cluster.ssh
	(:gen-class)
	(:require [clojure.edn :as edn]
		        [clojure.java.shell :as shell]
            [clojure.string :as str]
            [wgctrl.utils.main :refer [create-temp-file] ]))

(defn node-reg-data 
  "Gets data from node"
  [node]
  (-> (shell/sh "ssh" (:address node) "cat" "/root/.wg-node") 
       :out
       (edn/read-string)
       (conj {:location (:location node)})
       (conj {:dns (:dns node)})))





(defn peer!
 "Creates peer on WG node"
 [keys interface ip]
   (let [pubkey (-> (shell/sh "wg" "pubkey" :in (str (:key keys))) :out str/trim-newline)
         f (str/replace (:psk keys) #"/" "" )]
    (spit (str "/tmp/" f) (:psk keys))
    (println "PSK " (slurp (str "/tmp/" f)))
    
    (println "RUNNING SCP")
    (let [{:keys [err out exit]} 
      (shell/sh "scp" (str "/tmp/" f) 
        (str "root@" (:inet (.endpoint interface)) ":/tmp"))]
      
      (println exit err out (str "/tmp/" f)))

    (println "RUNNING SHELL" "ssh" (str "root@" (:inet (.endpoint interface)))
                "wg" "set" (.name interface) 
                "peer" pubkey 
                "allowed-ips" (str ip "/32") 
                "preshared-key"  (str "/tmp/" f))
    (let [cmd
      (shell/sh "ssh" (str "root@" (:inet (.endpoint interface)))
                "wg" "set" (.name interface) 
                "peer" pubkey 
                "allowed-ips" (str ip "/32") 
                "preshared-key"  (str "/tmp/" f))]
      (println cmd (str "/tmp/" f)))
      ))

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


