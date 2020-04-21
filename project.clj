(defproject morale-officer "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/core.cache "1.0.207"]
                 [reaver "0.1.3"]
                 [clj-http "3.10.1"]]
  :main ^:skip-aot morale-officer.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all} :dev {:dependencies [[org.clojure/test.check "0.9.0"]]}}
  )
