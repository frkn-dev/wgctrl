(ns wgctrl.cluster.stat)

(defn node 	[nodes]
  (reduce (fn [acc i]
            (conj acc {:name (.name i)
                     :peers (count @(.peers i))})) [] @(.interfaces nodes)))

(defn cluster [nodes]
  (reduce (fn [s n]
            (conj s {:node (.uuid n)
                     :hostname (:hostname n)
                     :stat (node n)})) [] nodes))