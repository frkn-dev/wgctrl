#!/bin/env bb

(require '[babashka.fs :as fs])

(defn registered? []
  (fs/exists? "/root/.wg-node2"))


(defn -main []
  (if (registered?)
    (System/exit 0)
    (System/exit 255)))

(-main)
