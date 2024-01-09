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

(def wg-config-filename "/opt/amnezia/wireguard/wg0.conf")
(def wg-config-public   "/opt/amnezia/wireguard/wireguard_server_public_key.key")
(def wg-config-psk      "/opt/amnezia/wireguard/wireguard_psk.key")

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
  (let [f (str (System/getenv "HOME") "/.wg-node2")]
    (and (fs/exists? f)
         (> (fs/size f) 0))))

(defn ip-addr [iface]
  (->> (oq (str "select address from interface_addresses where interface = '" iface "'"))
    (filter #(ipv4? (:address %)))
    first))


(defn wg-container-id []
  (:id (first (oq (str "SELECT name, id from docker_containers where name = '/amnezia-wireguard'" )))))

(defn wg-file [container file] 
  (let [{:keys [exit out err]} (sh "/usr/bin/docker" "exec" container "cat" file )]
    (if (zero? exit)
       out
      (println (str "ERROR Getting Amneia WG Config -> " err )))))


(defn wg-port [config]
  "Return Port of WG interface"
  (->> 
    (-> config
        (str/split #"\n"))
    (map #(str/split % #" "))
    (filter (fn [x] (= (first x) "ListenPort")))
    first
    last))

(defn wg-subnet [config]
  "Returns Subnet of WG Interface"
  (let [s (->> 
    (-> config
         (str/split #"\n"))
    (map #(str/split % #" = "))
    (filter #(= (first %) "Address"))
    first
    last)]
    (zipmap [:inet :inet6] (str/split s #"," ))))

(defn wg-settings [interface]
  (let [config (wg-file (wg-container-id) wg-config-filename)]
  {:name interface,
   :subnet (-> (wg-subnet config) :inet),
   :container_id (wg-container-id)
   :port (wg-port config)
   :public-key (-> (wg-file (wg-container-id) wg-config-public)
                 str/trim-newline)
   :psk (-> (wg-file (wg-container-id) wg-config-psk)
            str/trim-newline)}))

(defn uuid [] (.toString (java.util.UUID/randomUUID)))

(defn -main []
  (let [f (str (System/getenv "HOME") "/.wg-node2")
        i (default-interface)
        e (ip-addr i)
        u (uuid)
        h (-> (shell/sh "hostname") :out str/trim-newline)]
    (if (wg-node-registered?) 
            (do (println "Node already registered")
                (System/exit 255))
            (do (spit f {:uuid u  :type "wg" :hostname h :default-interface i :wg (wg-settings "wg0") :user "root" :endpoint (:address e)} )
                (System/exit 0)
          ))))

(-main)
  


