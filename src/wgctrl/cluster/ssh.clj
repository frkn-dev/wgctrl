(ns wgctrl.cluster.ssh
  (:require [clojure.edn :as edn]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
           ))

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
        f (str/replace (:psk keys) #"/" "")]

    (spit (str "/tmp/" f) (:psk keys))
    (let [{:keys [err out exit]}
          (shell/sh "scp" (str "/tmp/" f)
                    (str "root@" (:inet (.endpoint interface)) ":/tmp"))]

      (if (= 0 exit)
        (shell/sh "ssh" (str "root@" (:inet (.endpoint interface)))
                  "wg" "set" (.name interface)
                  "peer" pubkey
                  "allowed-ips" (str ip "/32")
                  "preshared-key"  (str "/tmp/" f))
        {:err err :out :exit}))))

