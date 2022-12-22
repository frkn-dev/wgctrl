(ns wgctrl.http.handlers
    (:gen-class)
    (:require [clojure.string :as str]
    	      [cheshire.core :as json]
    	      [wgctrl.cluster.model :as m]
    	      [wgctrl.cluster.stat :as stat]))



(defn peer [request]
	(println "TEST "  request m/cluster)
	(str "Works!\n" @( :nodes m/cluster ) ( :type m/cluster )))


(defn stat [request]    
    (let [stat (stat/cluster @(.nodes m/cluster))]
    	 (println "STAT " (json/generate-string (conj {:uuid (.uuid m/cluster)} {:nodes stat})))
    	 (json/generate-string (conj {:uuid (.uuid m/cluster)} {:nodes stat}))))




