(defproject frp "0.1.3"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.439"]
                 [aid "0.1.2"]
                 [aysylu/loom "1.0.2"]
                 [cljs-ajax "0.8.0"]
                 [clj-time "0.15.1"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]
                 [com.rpl/specter "1.1.2"]
                 [funcool/cats "2.3.1"]
                 [frankiesardo/linked "1.3.0"]
                 [jarohen/chime "0.2.2"]]
  :plugins [[com.jakemccrary/lein-test-refresh "0.19.0"]
            [lein-ancient "0.6.10"]
            [lein-doo "0.1.11"]
            [lein-npm "0.6.2"]]
  :profiles {:dev {:dependencies [[binaryage/devtools "0.9.10"]
                                  [com.taoensso/encore "2.105.0"]
                                  [figwheel-sidecar "0.5.17"]
                                  [org.clojure/test.check "0.9.0"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [spyscope "0.1.6"]]}}
  :cljsbuild
  {:builds {:test {:source-paths ["src" "test"]
                   :compiler     {:output-to  "dev-resources/public/js/main.js"
                                  :output-dir "dev-resources/public/js/out"
                                  :main       frp.test.doo
                                  :asset-path "/js/out"}}}}
  :doo {:paths {:karma "node_modules/karma/bin/karma"}}
  :npm {:dependencies [[karma "3.1.3"]
                       [karma-chrome-launcher "2.2.0"]
                       [karma-cljs-test "0.1.0"]]})
