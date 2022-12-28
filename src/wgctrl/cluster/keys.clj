(ns wgctrl.cluster.keys
  (:require [clojure.string :as str]
            [clojure.java.shell :as shell]))

(defn generate [server-pubkey]
  (let [client-key (-> (shell/sh "wg" "genkey") :out str/trim-newline)
        client-psk (-> (shell/sh "wg" "genpsk") :out str/trim-newline)
        client-pubkey (-> (shell/sh "wg" "pubkey" :in client-key) :out str/trim-newline)]

    { :client-key client-key,
      :client-psk client-psk,
      :client-pubkey client-pubkey
      :server-pubkey server-pubkey,}
))


