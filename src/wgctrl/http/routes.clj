(ns wgctrl.http.routes
  (:require [wgctrl.http.handlers :as handlers]
            [compojure.core :refer :all]
            [compojure.route :refer [not-found]]
            [ring.middleware.params :as params]))

(defroutes app-routes
  (GET "/" [] "<h2>=== Hello Fucking World!===</h2>")
  (GET "/peer" {params :query-params}  handlers/peer)
  (GET "/stat" [] handlers/stat)
  (GET "/locations" [] handlers/locations)
  (not-found "<h1>Page not found</h1>"))

(def app
  (-> app-routes
      params/wrap-params))