(ns wgctrl.http.routes
  (:require [wgctrl.http.handlers :as h]
            [wgctrl.cluster.nodes :refer [nodes]]
            [compojure.core :refer :all]
            [compojure.route :refer [not-found]]
            [ring.middleware.params :as params]))


(defroutes app-routes
  (GET "/" [] "<h2>=== Hello Fucking World!===</h2>")
  (GET "/peer" [pubkey location]  h/peer)
  (GET "/stat" [] h/stat)
  (GET "/peers-stat" [] h/peers-stat)
  (GET "/peers-amount" [] h/peers-amount)
  (GET "/locations" [] h/locs)
  (not-found "<h1>Page not found</h1>"))

(def app
  (-> app-routes
      params/wrap-params))

