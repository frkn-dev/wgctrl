(ns wgctrl.http.routes
	(:require [wgctrl.http.handlers :as handlers]
		      [compojure.core :refer :all]
		      [compojure.route :refer [not-found]]))


(defroutes app
    (GET "/" [] "<h2>=== Hello Fucking World!===</h2>")    
    (GET "/peer" [] handlers/peer) 
    (GET "/stat" [] handlers/stat)
    (not-found "<h1>Page not found</h1>"))