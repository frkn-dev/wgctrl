(ns wgctrl.ssh.nodes
  (:require [clojure.edn :as edn]
    [clojure.java.shell :refer [sh]]
    [clojure.string :as str]
    [wgctrl.ssh.common :as ssh]
    [clojure.tools.logging :as log])
  (:use [clojure.walk :only [keywordize-keys]]))


(defn register-node [node]
  (ssh/run-remote-script-bb node "./scripts/node-register-wg.bb"))

(defn node-registered? [node]
  (let [{:keys [exit]} (ssh/run-remote-script-bb node "./scripts/node-registered-check.bb")]
    (if (zero? exit)
      true
      false)))

(defn node-reg-data
  "Gets data from node or register node"
  [node]
  (let [{:keys [address user location dns weight active]} node]
     (log/info  location dns weight)
    (if (node-registered? node) 
      (-> (sh "ssh" (str user "@" address) "cat" "/root/.wg-node2")
        :out
        (edn/read-string)
        (conj {:location location})
        (conj {:dns dns})
        (conj {:weight weight})
        (conj {:active active }))
      (do (register-node node)
        (node-reg-data node)))))











