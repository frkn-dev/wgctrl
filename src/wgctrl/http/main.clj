(ns wgctrl.http.main
	(:gen-class)
  (:require [org.httpkit.server :as httpkit]
            [compojure.core :refer [defroutes GET]]
            [compojure.route :as route]
            [wgctrl.http.handlers :as h]))




(defroutes app 
  (GET "/" [] "<h2>=== Hello Fucking World!===</h2>")
  (GET "/peer" [] h/peer)
  (GET "/stat" [] h/stat) 
  (route/not-found "<h1>Page not found</h1>"))

(defn start-server [options]
  (httpkit/run-server #'app options))

(defn stop-server [server]
  (when-not (nil? @server)
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    ;; :timeout is optional, when no timeout, stop immediately
    (@server :timeout 100)
    (reset! server nil)))

