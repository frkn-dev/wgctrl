(ns wgctrl.utils.subnet
	(:gen-class)
	(:require [clojure.string :as str]))

(defn mask 
  "Cuts mask of subnet from address"
  [subnet]
  (->
    (str/split subnet #"/")
    last
    (str/replace #"," "")
    (Integer/parseInt)))

(defn addr 
  "Cuts address of subnet from mask"
  [subnet]
  (->>
    (str/split subnet #"/")
    first))

(defn size 
  "Calculates size of subnet by mask"
  [mask]
  (- (Math/pow 2 (- 32 mask)) 2))