(ns wg-agent.core
  (:gen-class)
  (:require [org.httpkit.server :as httpkit]
            [compojure.core :refer [defroutes GET]]
            [compojure.route :as route]
            [clojure.core.async :as async :refer [go]]
            [wg-agent.wg :as wg]
            [wg-agent.cluster :as c])
  (:use [clojure.walk :only [keywordize-keys]]))


(def config {:dns "1.1.1.1"})


(let [c (cluster! "dev")
      n (node! {:dns "1.1.1.1"})]
  (node->cluster n c))


(defn run-bash-script [script-path args]
  (let [{:keys [out err]} (sh "bash" script-path args)]
    (str out)))

(defn login-handler []
  (go
    (let [result (run-bash-script (:bash-script (httpkit/get-options)) args)]
      {:status 200 :body result})))

(defroutes app
  (GET "/" [] "<h1>Hello World</h1>")
  ;(GET "/peer" [] peer)
  (route/not-found "<h1>Page not found</h1>"))

(defn start-server [options]
  (httpkit/run-server app options))

(start-server app {:port 8080})