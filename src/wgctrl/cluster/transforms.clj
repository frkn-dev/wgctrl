(ns wgctrl.cluster.transforms)

(defn peer->interface
  "Adds peer to node's interface"
  [peer interface]
  (swap! (.peers interface) conj peer)
  interface)

(defn node->cluster [node cluster]
  (swap! (.nodes cluster) conj node)
  cluster)

(defn interface->node
  [interface node]
  (swap! (.interfaces node) conj interface)
  node)



