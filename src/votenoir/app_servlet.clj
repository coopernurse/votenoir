(ns votenoir.app_servlet
  (:gen-class :extends javax.servlet.http.HttpServlet)
  (:require [noir.util.gae :as noir-gae])
  (:require [appengine-magic.core :as ae])
  (:use [appengine-magic.servlet :only [make-servlet-service-method]]))

(require 'votenoir.ui)
(ae/def-appengine-app votenoir-app (noir-gae/gae-handler nil))

(defn -service [this request response]
  ((make-servlet-service-method votenoir-app) this request response))
