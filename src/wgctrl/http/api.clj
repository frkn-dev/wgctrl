(ns wgctrl.http.api 
  (:require  [mount.core :refer [defstate]]
    [org.httpkit.server :as httpkit]
    [wgctrl.http.routes :as routes]
    [wgctrl.config :refer [config]]))


(defstate api-server :start (httpkit/run-server #'routes/app {:port (:api-port config)})
  :stop (api-server :timeout 100))
