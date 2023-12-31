(defproject wgctrl "0.2.1-SNAPSHOT"
  :description "Controls WireGuard Cluster"
  :license {:name "GPL-3.0"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/tools.logging "1.2.4"]
                 [http-kit "2.6.0"]
                 [org.clojure/core.async "1.6.673"]
                 [compojure "1.7.0"]
                 [nrepl "1.0.0"]
                 [cheshire "5.11.0"]]    
  :plugins [[lein-cljfmt "0.9.0"]]                              
  :main ^:skip-aot wgctrl.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})

