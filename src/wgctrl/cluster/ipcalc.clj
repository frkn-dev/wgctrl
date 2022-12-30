(ns wgctrl.cluster.ipcalc
  (:require [clojure.string :as str]))

(defn addr->string
  "Transofrms IP address from vector to string"
  [a]
  (if (nil? a)
    nil
    (str/join #"." a)))

(defn addr->vec
  "Transforms IP address from string to vector"
  [a]
  (let [a' (->>
            (str/split a #"/")
            first)]
    (->> (str/split a' #"\.")
         (mapv #(Integer/parseInt %)))))

(defn int->addr
  "Transforms IP address from uint to string"
  [i]
  (if (nil? i)
    nil
    (addr->string [(bit-shift-right (bit-and i 0xff000000) 24)
                 (bit-shift-right (bit-and i 0x00ff0000) 16)
                 (bit-shift-right (bit-and i 0x0000ff00) 8)
                 (bit-and i 0x000000ff)])))

(defn addr->int
  "Transforms IP address from string to uint"
  [a]
  (if (nil? a)
    nil
    (let [a' (addr->vec a)]
      (bit-or
       (bit-shift-left (nth a' 0) 24)
       (bit-shift-left (nth a' 1) 16)
       (bit-shift-left (nth a' 2) 8)
       (nth a' 3)))))

(defn addr++
  "Increments IP address"
  [a]
  (let [a' (inc (addr->int a))]
    (if (> a' (Math/pow 2 32))
      nil
      (int->addr  a'))))

(defn addr-
  "Calculates range between 2 IP addresses "
  [a b]
  (let [a' (addr->int a)
        b' (addr->int b)]
    (- a' b')))

(defn addr>
  "Compares 2 IP addresses"
  [a b]
  (let [a'  (addr->int a)
        b' (addr->int b)]

    (if (> a' b')
      true
      false)))

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
  (if (nil? subnet)
    nil
    (->>
     (str/split subnet #"/")
     first)))

(defn size
  "Calculates size of subnet by mask"
  [mask]
  (- (Math/pow 2 (- 32 mask)) 2))