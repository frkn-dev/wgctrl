(ns wgctrl.dev 
  (:require [mount.core :as mount :refer [defstate]]
    [clojure.core.async :refer [chan timeout go-loop <! >! >!! <!!]]
    [clojure.tools.namespace.repl :as tn]
    [clojure.java.shell :refer [sh]]
    [clojure.string :as str]
    [mount.tools.graph :refer [states-with-deps]]
    [wgctrl.logging :refer [with-logging-status]]
    [clojure.java.jdbc :as jdbc]
    [clojure.edn :as edn]
    [clojure.tools.logging :refer [info]]
    [clojure.spec.alpha :as s]
    [clojure.test.check.generators :as gen]
    
    
    [wgctrl.config :refer [config]]
    [wgctrl.cluster.balancer :refer [balancers]]
    [wgctrl.cluster.ipcalc :as ip]
    [wgctrl.cluster.nodes :refer [nodes node!]]
    [wgctrl.cluster.peers :refer [addr!]]
    [wgctrl.nrepl :refer [nrepl-server]]
    [wgctrl.http.api :refer [api-server]]
    
    [wgctrl.ssh.common :as ssh]
    [wgctrl.db :as db]

    [wgctrl.ssh.nodes :as ssh-nodes]
    [wgctrl.ssh.peers :as ssh-peers]))

(defn start []
  (with-logging-status)
  (mount/start #'wgctrl.config/config
    #'wgctrl.cluster.nodes/nodes
    #'wgctrl.cluster.balancer/balancers
    #'wgctrl.db/db
    #'wgctrl.http.api/api-server
    #'wgctrl.nrepl/nrepl-server))

(defn stop []
  (mount/stop))

(defn refresh []
  (stop)
  (tn/refresh))

(defn refresh-all []
  (stop)
  (tn/refresh-all))

(defn go
  "starts all states defined by defstate"
  []
  (start)
  :ready)

(defn reset
  "stops all states defined by defstate, reloads modified source files, and restarts the states"
  []
  (stop)
  (tn/refresh :after 'wgctrl.dev/go))

(mount/in-clj-mode)




(comment 
  (start)
  (mount/start #'wgctrl.config/config)
  
  (-> config)
  
  (s/def ::ip-address
    (letfn [(pred [s]
              (let [parts (str/split s #"\.")]
                (and (= (count parts) 4)
                  (every? (fn [part]
                            (try
                              (let [n (edn/read-string part)]
                                (and (integer? n)
                                  (>= 256 n 0)))
                              (catch Exception _ false)))
                    parts))))
            (gen []
              (gen/fmap (partial str/join ".") (gen/vector (gen/choose 0 255) 4)))]
      (s/spec pred :gen gen)))
  
  (defn )
  
  (s/valid? ::ip-address (ip/addr "10.8.1.22/32"))
  
  (def peers1 (ssh-peers/peers (first nodes)))
  
  (-> peers1 first )
  
  (ip/addr "10.0.0.1/32")
  
  (defn valid-peers [peers]
    (filter #(s/valid? ::ip-address (ip/addr (:ip %))) peers))
  
 (-> (valid-peers peers1))
  

  
  (defn register-node [node]
    (do (ssh/run-remote-script-sh-docker node "./scripts/tweak-wg.sh")
      (ssh/run-remote-script-bb node "./scripts/node-register-wg.bb")))

  

  (jdbc/query db/db "select * from peers")
  
  (defn restore-node [node]
    (let [id (:uuid node)
          exist-peers (mapv #(:peer %) (ssh-peers/peers node))
          peers (jdbc/find-by-keys db/db :peers {:node_id id})]
      (let [peers' (map #(:peer %) (vec (clojure.set/difference 
                                          (set (map #(select-keys % [:peer]) peers))
                                          (set (map #(hash-map :peer %) exist-peers)))))]
        (for [peer peers']
          (let [peer' (first (jdbc/find-by-keys db/db :peers {:peer peer}))]
            (ssh-peers/restore! node (:peer peer') (:ip peer')))))))
  
  
  (defn restore-node2 [node]
    (let [id (:uuid node)
          exist-peers (mapv #(:peer %) (ssh-peers/peers node))
          peers (jdbc/find-by-keys db/db :peers {:node_id id})]
      (let [peers' (map #(:peer %) (vec (clojure.set/difference 
                                          (set (map #(select-keys % [:peer]) peers))
                                          (set (map #(hash-map :peer %) exist-peers)))))]
        peers
        )))
  
  (restore-node2 (first nodes))
  
 (restore-node (first nodes))
  
  (mapv #(:peer %) (ssh-peers/peers (first nodes)))
  
  
  (db/peer! {:peer "TEST" :node-id "7dfbbeec-79a7-4261-8f47-2e546c8ab35a" :ip "IP"})
  
  (jdbc/find-by-keys db/db :peers {:node_id (-> nodes first :uuid)})
  
  (-> db/db)
  
  (-> config)
  
(defn table-exist? [t]
  (try
    (jdbc/query db/db (str "SELECT * FROM " t))
    true
    (catch Exception e
      false)))  
  
  (table-exist? "peers")
  
  (ssh/run-remote-script-sh-docker (first nodes) "./scripts/restore-peer.sh" "uPp/X7kACLWn4eeMQs/hhTrprnhJ7xyEU94iKxlI9ho=" "10.8.1.9/32")
  
  (ssh-nodes/restore-node (first nodes ))
  
  (jdbc/query db/db "SELECT * FROM peers")
  
 (frequencies (map #(ip/addr (:ip %)) (jbc/find-by-keys db/db :peers {:node_id (:uuid (first nodes))})))

(-> db/db)

  
 (frequencies(set (map #(:ip %) (jdbc/find-by-keys db/db :peers {:node_id (:uuid (first nodes))})))
  
  
  
  (ssh-peers/valid-peers (jdbc/find-by-keys db/db :peers {:node_id (:uuid (first nodes))}))
  
  (ssh-peers/valid-peers (ssh-peers/peers (first nodes)))

  )
