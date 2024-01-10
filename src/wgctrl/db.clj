(ns wgctrl.db
  (:require [clojure.java.jdbc :as jdbc]
    [mount.core :as mount]
    [clojure.string :as str]
    [wgctrl.logging :refer [with-logging-status]]
    [wgctrl.ssh.peers :as ssh]))

(def spec
  {:classname   "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname     ":memory:"})

(def db-uri "jdbc:sqlite::memory:")

(declare db)

(defn on-start []
  (let [spec {:connection-uri db-uri}
        conn (jdbc/get-connection spec)]
    (assoc spec :connection conn)))

(defn on-stop []
  (-> db :connection .close)
  nil)

(mount/defstate
  ^{:on-reload :noop}
  db
  :start (on-start)
  :stop (on-stop))

(mount/start #'wgctrl.db/db)

(def table-name "peers")

(def table-query (str "CREATE TABLE " table-name
                   " (peer VARCHAR(64),
                     id INTEGER NOT NULL PRIMARY KEY,
                     node_id VARCHAR(32),
                     created_at VARCHAR(10),
                     ip VARCHAR(64))"))

(defn table-exist? [t]
  (try
    (jdbc/query db (str "SELECT * FROM " t))
    true
    (catch Exception e
      false)))


(defn prepare-insert [peer node-id ip]
  {:peer  peer,
   :node_id node-id,
   :created_at (System/currentTimeMillis),
   :ip ip,
   })

(defn peer! [{:keys [peer node-id ip]}]
  (jdbc/insert! db table-name (prepare-insert peer node-id ip )))

(defn bulk-peer-insert [rows]
  (let [r (map #(prepare-insert (:peer %) (:node-id %) (:ip %)) rows)]
    (jdbc/insert-multi! db table-name r)))

(defn peer-loaded? [{:keys [peer]}]
  (not(empty? (jdbc/find-by-keys db :peers {:peer peer}))))

(defn peers->db [nodes]
  (bulk-peer-insert db 
    (mapcat #(->> (ssh/peers %) 
               (remove peer-loaded?)) nodes )))

(defn peer->db [peer]
  (if (peer-loaded? peer)
    nil
    (peer! peer)))


