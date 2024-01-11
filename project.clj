(defproject wgctrl "0.2.1-SNAPSHOT"
  :description "Controls WireGuard Cluster"
  :license {:name "GPL-3.0"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/tools.logging "1.2.4"]
                 [robert/hooke "1.3.0"]
                 [http-kit "2.6.0"]
                 [org.clojure/core.async "1.6.673"]
                 [compojure "1.7.0"]
                 [nrepl "1.0.0"]
                 [cheshire "5.11.0"]
                 [mount "0.1.16"]
                 [org.clojure/tools.namespace "1.4.4"]
                 [org.clojure/java.jdbc "0.7.12"]
                 [org.xerial/sqlite-jdbc "3.42.0.0"]
                 [org.clojure/clojure "1.11.1"]
                 [org.clojure/test.check "0.10.0"]]
  :plugins [[lein-cljfmt "0.9.0"]]                              
  :main ^:skip-aot wgctrl.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})

