(ns wgctrl.cluster.keys
  (:require [clojure.string :as str]
            [clojure.java.shell :as shell]))

(defn generate-client [interface]
  (let [client_key (-> (shell/sh "wg" "genkey") :out str/trim-newline)
        client_psk (-> (shell/sh "wg" "genpsk") :out str/trim-newline)
        client_pubkey (-> (shell/sh "wg" "pubkey" :in client_key) :out str/trim-newline)
        server_key (.key interface)
        server_pubkey (-> (shell/sh "wg" "pubkey" :in server_key) :out str/trim-newline)]

    {:psk client_psk,
     :pubkey server_pubkey,
     :key client_key}))



