(ns votenoir.config
  (:use [noir.core :only (defpage defpartial render)])
  (:use [hiccup.form-helpers])
  (:require [votenoir.db :as db])
  (:require [votenoir.ui :as ui])
  )

(defpartial config-row [c]
  (let [id (str "delete-" (str (:id c)))]
    [:tr
     [:td [:input {:type "checkbox" :name id :value "1"} ]]
     [:td (:id c)]
     [:td (:value c)]]))

(defpartial config-view [configs]
  (ui/layout "Configuration"
    (ui/wrap-form "/admin/config"
      (ui/form-row-full
        "Add/edit properties. One per line. Format: key=value"
        (text-area {:class "radius input-text":rows 10 :cols 40 } "props"))
      [:table
       [:tr [:th "Delete"] [:th "Key"] [:th "Value"] ]
       (map config-row configs)
       ]
      (ui/submit-row "Save"))))

(defn config-page
  []
  (render config-view (db/get-all-config)))

(defn delete-prop
  [k]
  (let [key (subs (str k) 8)]
    (db/delete-config-by-id key)))

(defn delete-props
  [params]
  (doall (map delete-prop (filter #(.startsWith (str %) ":delete-") (keys params)))))

(defn save-prop
  [line]
  (let [pos (.indexOf line "=")]
    (if (> pos -1)
      (let [key (subs line 0 pos)
            val (subs line (+ 1 pos))]
        (db/put-config key val)))))

(defn save-props
  [p]
  (doall (map save-prop (ui/split-newline p))))

(defpage [:get "/admin/config"] {:as params}
  (config-page))

(defpage [:post "/admin/config"] {:as params}
  (delete-props params)
  (save-props (:props params))
  (config-page))
  