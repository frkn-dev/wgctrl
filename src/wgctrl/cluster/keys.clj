(ns wgctrl.cluster.keys
  (:require [clojure.string :as str]
            [clojure.java.shell :as shell]))

(defn- generate []
  (let [key (-> (shell/sh "wg" "genkey") :out str/trim-newline)
        pubkey (-> (shell/sh "wg" "pubkey" :in key) :out str/trim-newline)]

    {:key key,
     :pubkey pubkey}))

(defn client [pubkey]
  (if (nil? pubkey)
    (generate)
    {:pubkey pubkey})) 

