(ns wgctrl.cluster.stat
	(:gen-class))

(defn node 	[nodes] 
  (reduce (fn [s i] 
  	        (conj s {:name (.name i) 
  	        	     :peers (count @(.peers i))})) [] @(.interfaces nodes) ))

(defn cluster [nodes]
  (reduce (fn [s n] 
  	        (conj s {:node (.uuid n) 
  	        	     :hostname (:hostname n) 
  	        	     :stat (node n)})) [] nodes ))