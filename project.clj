(defproject wgctrl "0.1.0-SNAPSHOT"
  :description "Controls WireGuard Cluster"
  :license {:name "GPL-2.0-or-later WITH Classpath-exception-2.0"}
  :dependencies [[org.clojure/clojure "1.10.0"]
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

