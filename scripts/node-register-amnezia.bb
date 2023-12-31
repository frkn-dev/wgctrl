#!/bin/env bb
;; Node register script  
;; Reads system settings and creates ~/.wg-node file 

;;(ns node-register.main 
  (require '[babashka.fs :as fs]
	         '[cheshire.core :as json]
           '[clojure.string :as str]
           '[clojure.java.shell :refer [sh]]
           '[cheshire.core :as json])
(use '[clojure.walk :only [keywordize-keys]])

(def amnezia-wg-config-filename "/opt/amnezia/wireguard/wg0.conf")
(def amnezia-wg-config-public   "/opt/amnezia/wireguard/wireguard_server_public_key.key")
(def amnezia-wg-config-psk      "/opt/amnezia/wireguard/wireguard_psk.key")

(defn oq [query]
  (let [{:keys [exit out err]} (sh "osqueryi" "--json" query)]
    (if (zero? exit)
      (json/decode out true)
      (throw (Exception. err)))))

(defn ipv4? [ip-addr]
  (some? (re-matches #"\d+\.\d+\.\d+\.\d+" ip-addr)))

(defn default-interface 
  "Parses output of 'ip -json route and gets default interface;"
  [] 
  (->> 
    (-> (sh "ip" "-json" "route") 
         :out
         json/parse-string
         keywordize-keys)
    (filter #(= (:dst %) "default"))
     first
     :dev))

(defn wg-node-registered? []
  (let [f (str (System/getenv "HOME") "/.wg-node")]
    (and (fs/exists? f)
         (> (fs/size f) 0))))

(defn ip-addr [iface]
  (->> (oq (str "select address from interface_addresses where interface = '" iface "'"))
    (filter #(ipv4? (:address %)))
    first))


(defn amnezia-wg-container-id []
  (:id (first (oq (str "SELECT name, id from docker_containers where name = '/amnezia-wireguard'" )))))

(defn amnezia-wg-file [container file] 
  (let [{:keys [exit out err]} (sh "/usr/bin/docker" "exec" container "cat" file )]
    (if (zero? exit)
       out
      (println (str "ERROR Getting Amneia WG Config -> " err )))))


(defn amnezia-wg-port [config]
  "Return Port of WG interface"
  (->> 
    (-> config
        (str/split #"\n"))
    (map #(str/split % #" "))
    (filter (fn [x] (= (first x) "ListenPort")))
    first
    last))

(defn amnezia-wg-subnet [config]
  "Returns Subnet of WG Interface"
  (let [s (->> 
    (-> config
         (str/split #"\n"))
    (map #(str/split % #" = "))
    (filter #(= (first %) "Address"))
    first
    last)]
    (zipmap [:inet :inet6] (str/split s #"," ))))

(defn amnezia-wg-settings [interface]
  (let [config (amnezia-wg-file (amnezia-wg-container-id) amnezia-wg-config-filename)]
  {:name interface,
   :subnet (amnezia-wg-subnet config),
   :container_id (amnezia-wg-container-id)
   :port (amnezia-wg-port config)
   :public-key (-> (amnezia-wg-file (amnezia-wg-container-id) amnezia-wg-config-public)
                 str/trim-newline)
   :psk (-> (amnezia-wg-file (amnezia-wg-container-id) amnezia-wg-config-psk)
            str/trim-newline)
   :type "amnezia-wg"}))

(defn uuid [] (.toString (java.util.UUID/randomUUID)))


(defn -main []
  (let [f (str (System/getenv "HOME") "/.wg-node")
        i (default-interface)
        e (ip-addr i)
        u (uuid)
        h (-> (shell/sh "hostname") :out str/trim-newline)]
    (if (wg-node-registered?) 
            (do (println "Node already registered")
                (System/exit 255))
            (do (spit f {:uuid u :hostname h :default-interface i :interfaces [(conj (amnezia-wg-settings "wg0") {:endpoint (:address e)})]})
                (System/exit 0)
          ))))

(-main)
  


