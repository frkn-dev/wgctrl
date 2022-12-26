(ns wgctrl.http.handlers
  (:require [clojure.string :as str]
            [cheshire.core :as json]
            [wgctrl.cluster.model :as m]
            [wgctrl.cluster.transforms :as t]
            [wgctrl.cluster.selectors :as s]
            [wgctrl.cluster.ssh :as ssh]
            [wgctrl.cluster.keys :as keys]
            [wgctrl.cluster.stat :as stat]
            [wgctrl.cluster.utils :as utils]))

(defn peer [request]
  (let [i (s/interface-with-min-peers (s/node-with-min-peers @(.nodes m/cluster) "dev"))
        keys (keys/generate-client i)
        ip (utils/addr! i)
        uuid (.toString (java.util.UUID/randomUUID))]

    (let [{:keys [err exit]} (ssh/peer! keys i ip)]

      (if (= 0 exit)
        (do (t/peer->interface (m/peer! [uuid
                                         "test"
                                         (:psk keys)
                                         (:pubkey keys)
                                         (:key keys)
                                         (str ip "/32")]) i)

            (json/generate-string {:interface {:address (str ip "/24")
                                               :key (:key keys)
                                               :dns "1.1.1.1"}
                                   :peer {:pubkey (:pubkey keys)
                                          :psk  (:psk keys)
                                          :allowed_ips "0.0.0.0/0"
                                          :endpoint (str (-> i .endpoint :inet) ":" (-> i .port))}}))
        {:body (json/generate-string {:err "Can't create peer" :message err})
         :code 200
         :headers {"Content-Type" "text/html; charset=utf-8"}}

            ))))

(defn stat [request]
  (let [stat (stat/cluster @(.nodes m/cluster))]
    (println "STAT " (json/generate-string (conj {:uuid (.uuid m/cluster)} {:nodes stat})))
    {:body (json/generate-string (conj {:uuid (.uuid m/cluster)} {:nodes stat}))
     :code 200
     :headers {"Content-Type" "text/html; charset=utf-8"}}
     ))



