(ns wgctrl.ssh.common
  (:require [clojure.edn :as edn]
    [clojure.java.shell :refer [sh]]
    [clojure.string :as str])
  (:use [clojure.walk :only [keywordize-keys]]))


(defn run-remote-script-bb [{:keys [user address]} script]
  (let [remote-command (str "ssh "user "@" address " 'bb' < " script)]
    (sh "/bin/bash" "-c" remote-command)))

(defn run-remote-script-sh-docker [node script ip]
  (let [remote-command (str "ssh " (:user node) "@" (:endpoint node) " 'docker exec -i' " (-> node :interface :container) " bash -s < " script " " ip)]
    (sh "/bin/bash" "-c" remote-command)))

