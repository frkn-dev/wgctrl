#!/bin/env bb
;; Node register script  
;; Reads system settings and creates ~/.wg-node file 

;(ns node-register.main )
  (require '[babashka.fs :as fs]
	         '[cheshire.core :as json]
           '[clojure.string :as str]
           '[clojure.java.shell :as shell])
(use '[clojure.walk :only [keywordize-keys]])

(defn default-interface 
  "Parses output of 'ip -json route and gets default interface;"
  [] 
  (->> 
    (-> (shell/sh "ip" "-json" "route") 
         :out
         json/parse-string
         keywordize-keys)
    (filter #(= (:dst %) "default"))
     first
     :dev))


(defn endpoint [iface]
  "Returns IP addresses if Interface"
  (->> 
    (-> (shell/sh "ip" "-json" "address" "show" iface) 
        :out
        json/parse-string
        keywordize-keys
        vec
        first
        :addr_info)
    (remove #(= 8 (:prefixlen %)))
    (remove #(= "link" (:scope %)))
    (mapv #(:local %))
    (zipmap [:inet :inet6])))

; WireGuard Settings

(defn uuid [] (.toString (java.util.UUID/randomUUID)))

(defn port [iface]
  "Return ListenPort of WG interface"
  (->> 
  	(-> (str "/etc/wireguard/" iface ".conf") 
  	    slurp
  	    (str/split #"\n"))
  	(map #(str/split % #" "))
    (filter (fn [x] (= (first x) "ListenPort")))
    first
    last))

(defn wg-interfaces []
  "Returns list of all WG interfaces"
  (let [p "/etc/wireguard/"]
    (->> 
  	  (-> (shell/sh "ls" p)
  	    :out
  	    (str/split #"\n"))
      (mapv #(str/replace % #".conf" ""))
      (remove #(empty? (slurp (str p % ".conf"))))
      vec)))

(defn subnet [iface]
  "Returns Subnet of WG Interface"
	(->> 
	  (-> (str "/etc/wireguard/" iface ".conf") 
  	     slurp
  	     (str/split #"\n"))
	  (map #(str/split % #" "))
    (filter #(= (first %) "Address"))
    first
    (take-last 2)
    (zipmap [:inet :inet6])))

(defn wg-installed? []
  (fs/exists? "/etc/wireguard"))

(defn wg-node-registered? []
  (let [f (str (System/getenv "HOME") "/.wg-node")]
    (and (fs/exists? f)
         (> (fs/size f) 0))))


(defn wg-iface-public-key [private]
  (let [{:keys [exit out]} (shell/sh "wg" "pubkey" :in private)]
    (if (= exit 0)
      (-> out str/trim-newline)
      "FAKE PRIVATE KEY")))
  
(defn wg-iface-key [interface]
  "Return PrivateKey of WG interface"
  (->> 
    (-> (str "/etc/wireguard/" interface ".conf") 
        slurp
        (str/split #"\n"))
    (map #(str/split % #" "))
    (filter (fn [x] (= (first x) "PrivateKey")))
    first
    last))


(defn wg-settings [interface]
  {:name interface,
   :subnet (subnet interface),
   :port (port interface)
   :public-key (or (wg-iface-public-key (wg-iface-key interface))
                   "FAKE")})

  

(defn -main []
  (if (wg-installed?)
    (let [i (default-interface)
      e (endpoint i)
	    ifaces (wg-interfaces)
      u (uuid)
      h (-> (shell/sh "hostname") :out str/trim-newline)]
	    (let [f (str (System/getenv "HOME") "/.wg-node")]
        (if (wg-node-registered?) 
            (do (println "Node alreaady registered"))
            (do (spit f {:uuid u :hostname h :default-interface i :interfaces (mapv #(conj (wg-settings %) {:endpoint e}) ifaces) })
          ))))
    (println "Wireguard is not installed")))

(-main)
