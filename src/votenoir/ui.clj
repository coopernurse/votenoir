(ns votenoir.ui
  (:use [noir.core :only (defpage defpartial render)])
  (:use [hiccup.page-helpers])
  (:use [hiccup.form-helpers])
  (:require [noir.validation :as vali])
  (:require [noir.cookies :as cookies])
  (:require [votenoir.db :as db])
  (:require [vote.condorcet :as condorcet]))

(defmacro dbg[x] `(let [x# ~x] (println '~x "=" x#) x#))

(defn split-newline
  [x]
  (map clojure.string/trim (filter #(not (= "" %)) (clojure.string/split x #"\n"))))

(defn join-newline
  [xs]
  (clojure.string/join "\n" xs))

(defn create-uuid
  []
  (.toString (java.util.UUID/randomUUID)))

(defn create-user-id []
  (let [id (create-uuid)]
    (cookies/put! :user-id id)
    id))

(defn get-create-user-id []
  (let [id (cookies/get :user-id)]
    (if (nil? id)
      (create-user-id)
      id)))

(defn distinct-param-ids [params]
  (distinct (filter #(not (nil? %)) (map (fn [z] (second (clojure.string/split (str z) #"-"))) (keys params)))))

(defn indexed-param-to-map-pairs [params k v]
  (map (fn [kw] [((keyword (str k kw)) params) ((keyword (str v kw)) params)]) (distinct-param-ids params)))

(defn indexed-param-to-map [params k v]
  (apply array-map (apply concat (indexed-param-to-map-pairs params k v))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ui components ;;
;;;;;;;;;;;;;;;;;;;

(defpartial layout [title & content]
  (html5
    [:head
     [:title (str "clj-vote - " title)]]
    [:body
     [:h1 title]
     [:div content]
     [:div [:a {:href "/ballots"} "view ballots"]]
    ]))

(defpartial error-item [[first-error]]
  [:p.error first-error])

(defpartial ballot-table-row [b]
  [:tr
   [:td (:name b)]
   [:td [:a {:href (str "/vote/" (:id b))} "vote"]]
   [:td [:a {:href (str "/results/" (:id b))} "results"]]
   [:td [:a {:href (str "/edit-ballot/" (:id b))} "edit"]]
   [:td [:a {:href (str "/delete-ballot/" (:id b))} "delete"]]])

(defpartial ballot-table [ballots]
  [:table
   [:tr [:th "name"] [:th "vote"] [:th "results"] [:th "edit"] [:th "delete"]]
   (map ballot-table-row ballots)])

(defpartial ballot-form-fields [{:keys [name candidates]}]
  (vali/on-error :name error-item)
  (label "name" "name: ")
  (text-field "name" name)
  (vali/on-error :candidates error-item)
  (label "candidates" "candidates: ")
  (text-area {:rows 10 :cols 40 } "candidates" (join-newline candidates)))

(defpartial ballot-form [b]
  (form-to [:post "/save-ballot"]
    (if b (hidden-field "id" (:id b)))
    (ballot-form-fields b)
    (submit-button "save")))

(defpartial vote-candidate-field [x c]
  [:tr
   [:td c]
   [:td
    [:select {:name (str "score-" x) }
     (select-options (take 11 (iterate inc 0)))]
    (hidden-field (str "key-" x) c)
    ]
   ])

(defpartial vote-form-fields [{:keys [candidates]}]
  (vali/on-error :name error-item)
  [:table (map vote-candidate-field (iterate inc 0) candidates)])

(defpartial vote-form [b]
  (form-to [:post "/save-vote"]
    (hidden-field "id" (:id b))
    (vote-form-fields b)
    (submit-button "vote!")))

(defpartial ballots-view [ballots]
  (layout "ballots"
      (if (= 0 (count ballots))
        [:div "You have no ballots"]
        (ballot-table ballots))
      [:div [:a {:href "add-ballot"} "create new ballot"]]))

(defpartial add-edit-ballot-view [b]
  (layout (if b "edit ballot" "new ballot")
    (ballot-form b)))

(defpartial vote-view [b]
  (layout (str "vote on: " (:name b))
    [:div "Rate each option (10 = love it, 0 = hate it).  It's ok to rate options the same."]
    (vote-form b)))

(defpartial vote-saved-view []
  (layout "vote saved!")
    [:div "Thanks for voting"])

(defpartial results-view-matrix-row [pair]
  (let [matchup (first pair)]
    [:tr
     [:td (str (first matchup) " vs " (second matchup))]
     [:td (second pair)]
     ]))

(defpartial results-view [b winner matrix]
  (layout (str "results for: " (:name b))
    [:div [:b (str "winner: " winner) ]]
    [:table (map results-view-matrix-row matrix) ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ballot-valid? [{:keys [name candidates]}]
  (vali/rule (vali/min-length? name 3)
             [:name "Name must have at least 3 letters"])
  (vali/rule (vali/has-value? candidates)
             [:candidates "You must have some candidates"])
  (not (vali/errors? :name :candidates)))

(defn save-ballot-success [ballot user-id]
  (db/put-ballot
    (if (:id ballot) (:id ballot) (create-uuid))
    user-id
    (:name ballot)
    (split-newline (:candidates ballot)))
  (render "/ballots" ballot))

(defn save-vote [vote user-id]
  (db/put-vote
    (:id vote)
    user-id
    (indexed-param-to-map vote "key-" "score-")))

(defn transform-to-ranked-ballot [votes]
  (map (fn [m] (zipmap (keys m) (map (fn [v] (Math/abs (- 10 (Integer/parseInt v)))) (vals m)))) votes))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defpage "/" {:as req}
  (layout "home"
    [:span {:class "foo2"} "bar3"]))

(defpage "/ballots" {:as req}
  (ballots-view (db/get-ballots-by-user (get-create-user-id))))

(defpage "/add-ballot" {:as req}
  (add-edit-ballot-view nil))

(defpage "/edit-ballot/:id" {:keys [id]}
  (add-edit-ballot-view (db/get-ballot-by-id id)))

(defpage "/delete-ballot/:id" {:keys [id]}
  (db/delete-ballot-by-id id (get-create-user-id))
  (render "/ballots" nil))

(defpage [:post "/save-ballot"] {:as ballot}
  (if (ballot-valid? ballot)
    (save-ballot-success ballot (get-create-user-id))
    (render "/add-ballot" ballot)))

(defpage "/vote/:id" {:keys [id]}
  (vote-view (db/get-ballot-by-id id)))

(defpage [:post "/save-vote"] {:as vote}
  (save-vote vote (get-create-user-id))
  (vote-saved-view))

(defpage "/results/:id" {:keys [id]}
  (let [b      (db/get-ballot-by-id id)
        votes  (transform-to-ranked-ballot (db/get-votes-by-ballot-id id))
        matrix (condorcet/tab-ranked-pairs-matrix (:candidates b) votes)
        winner (condorcet/ranked-pairs-winner matrix)]
    (results-view b winner matrix)))
