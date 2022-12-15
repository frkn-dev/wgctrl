(ns wg-agent.linux 
	(:gen-class)
	(:require [clojure.string :as str]
		      [clojure.java.shell :as shell]
		      [cheshire.core :as json])
    (:use [clojure.walk :only [keywordize-keys]]))

(defn peer!
  "Should make peer for WG"
  ;; bash add-peer.sh wg0 94.176.238.220 10.7.0.11/32 fddd:2c4:2c4:2c4::11/128 dev2 51820
 [node name]
  (shell/sh "bash" "add-peer.sh" (interface) (endpoint) (allowed-ips) (name) (port)))


(defn default-interface 
  "Parses output of 'ip -json route and gets default interface;"
  [] 
  (if (ubuntu?)
    (let [routes (-> (shell/sh "ip" "-json" "route") 
                   :out
                 json/parse-string
                 keywordize-keys)]
        (:dev (nth (filter #(= (:dst %) "default") routes) 0)))
     "ens3"))


(defn endpoint4
  "Parses output of 'ip -json route and gets default interface;"
  [] 
  (let [ifaces (-> (shell/sh "ip" "-json" "address" "show" (default-interface)) 
                   :out
                   json/parse-string
                   keywordize-keys
                   vec)]
     (:local (first (filter #(= (:family %) "inet") (get-in ifaces [0 :addr_info]))))))

(defn endpoint6
  "Parses output of 'ip -json route and gets default interface;"
  [] 
  (let [ifaces (-> (shell/sh "ip" "-json" "address" "show" (default-interface)) 
                   :out
                   json/parse-string
                   keywordize-keys
                   vec)]
     (:local (first (filter #(= (:family %) "inet6") (get-in ifaces [0 :addr_info]))))))