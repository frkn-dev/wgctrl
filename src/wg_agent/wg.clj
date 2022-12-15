(ns wg-agent.wg 
	(:gen-class)
	(:require [clojure.string :as str]
		      [clojure.java.shell :as shell]))

(defn interfaces []
  (let [ifaces (str/split (-> (shell/sh "ls" "/etc/wireguard/") :out) #"\n")]
    (mapv #(str/replace % #".conf" "") ifaces)))

(defn port [iface]
  (let [path "/etc/wireguard/"]
    (let [line (->> (str/split (slurp (str path iface ".conf")) #"\n")
      (map #(str/split % #" "))
      (filter (fn [x] (= (first x) "ListenPort"))))]
    (last (first line)))))

(defn subnet
  "Reads config of wg interface and gets subnet"
  [iface]
  (let [path "/etc/wireguard/"]
    (let [line (->> (str/split (slurp (str path iface ".conf")) #"\n")
      (map #(str/split % #" "))
      (filter (fn [x] (= (first x) "Address")))
      first
      (filter (fn [x] (and (not (= x "Address" ))
                          (not (= x "=" )))))
      (map #(str/replace % #"," "")))]
      (zipmap [:inet :inet6] line))))

