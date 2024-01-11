(ns wgctrl.ssh.nodes
  (:require [clojure.edn :as edn]
    [clojure.java.shell :refer [sh]]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [clojure.java.jdbc :as jdbc]
    [wgctrl.ssh.common :as ssh]
    [wgctrl.ssh.peers :as ssh-peers]
    [wgctrl.db :as db])
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

(defn restore-node [node]
  (let [id (:uuid node)
        exist-peers (mapv #(:peer %) (ssh-peers/valid-peers (ssh-peers/peers node)))
        peers (jdbc/find-by-keys db/db :peers {:node_id id})]
    (let [peers' (map #(:peer %) (vec (clojure.set/difference 
                                            (set (map #(select-keys % [:peer]) (ssh-peers/valid-peers peers)))
                                            (set (map #(hash-map :peer %) exist-peers)))))]
      (for [peer peers']
        (let [peer' (first (jdbc/find-by-keys db/db :peers {:peer peer}))]
          (log/info "Restoring peer " peer')
          (ssh-peers/restore! node (:peer peer') (:ip peer'))))
      nil)))











