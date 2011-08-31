(defproject votenoir "1.0.0-SNAPSHOT"
  :description "Noir / appengine webapp for conducting polls"
  :dependencies [ [org.clojure/clojure "1.2.1"]
                  [org.clojure/clojure-contrib "1.2.0"]
                  [clj-appengine-oauth "0.1.0"]
                  [vote "0.5.0"]
                  [noir "1.1.1-SNAPSHOT"] ]
  :dev-dependencies [[appengine-magic "0.4.4"]])