(ns wgctrl.config 
  (:require [clojure.java.io :as io]
    [clojure.edn :as edn]
    [mount.core :refer [defstate]]))


(defn load-edn
  "Load edn from an io/reader source (filename or io/resource)."
  [source]
  (try
    (with-open [r (io/reader source)]
      (edn/read (java.io.PushbackReader. r)))
    (catch java.io.IOException e
      (printf "Couldn't open '%s': %s\n" source (.getMessage e)))
    (catch RuntimeException e
      (printf "Error parsing edn file '%s': %s\n" source (.getMessage e)))))


(defstate config
  :start (load-edn "./config.edn"))

