#!/bin/env bb
;; Add peer script  
;; Creates new peer and returns EDN to stdout 

(ns add-node.main )
(require '[babashka.fs :as fs]
	         '[cheshire.core :as json]
           '[clojure.string :as str]
           '[clojure.java.shell :as shell])
(use '[clojure.walk :only [keywordize-keys]])


(defn wg-installed? []
  (fs/exists? "/etc/wireguard"))

(defn iface-configured? [i]
  (let [f (str "/etc/wireguard/" i ".conf")]
    (and (fs/exists? f)
         (> (fs/size f) 0))))

(defn peer++ [input]
  (let [{:keys [peer_name interface endpoint_ip allowed_ipv4 endpoint_port]} input]
       (println "->>>INPUT " interface endpoint_ip allowed_ipv4 peer_name endpoint_port)
    (let [key (-> (shell/sh "wg" "genkey") :out str/trim-newline)
          psk (-> (shell/sh "wg" "genpsk") :out str/trim-newline)
          psk-tmp-file-path (.toString (fs/create-temp-file))
          pubkey (-> (shell/sh "wg" "pubkey" :in key ) :out str/trim-newline)]

       (spit psk-tmp-file-path  psk)
       (println interface key psk pubkey psk-tmp-file-path )

       (let [{:keys [out err]} (shell/sh "wg" "set" interface "peer" pubkey 
            "allowed-ips" allowed_ipv4 
            "preshared-key" psk-tmp-file-path "persistent-keepalive" "25"
            )]

            (println out err))

      (println {:name peer_name,
        :interface interface,
        :psk psk,
        :pubkey pubkey,
        :key key, 
        :allowed_ipv4 allowed_ipv4,})
  )))

(let [input (zipmap [:interface 
                     :endpoint_ip
                     :allowed_ipv4
                     :peer_name
                     :endpoint_port] *command-line-args*)]
     (when (or (not (iface-configured? (:interface input)))
               (not (wg-installed?)))
           (println {:err "wg not configured"})
           (System/exit 1))
     (when (not(= 5 (count input)))
       (println {:err "Wrong args"})
          (System/exit 1)) 

     (peer++ input))

(System/exit 0)

