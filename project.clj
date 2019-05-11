(defproject cash "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.logging "0.4.1"]
                 [com.datomic/datomic-free "0.9.5703"]
                 [clj-http "3.10.0"]
                 [environ "1.1.0"]
                 [metosin/compojure-api "2.0.0-alpha30"]
                 [aleph "0.4.6"]
                 [selmer "1.12.12"]
                 [ch.qos.logback/logback-classic "1.1.8"]
                 [ch.qos.logback/logback-core "1.1.8"]
                 [com.arohner/uri "0.1.2"]]
  :repl-options {:init-ns cash.core})
