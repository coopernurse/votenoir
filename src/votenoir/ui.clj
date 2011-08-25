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

(defpartial top-nav [title]
  [:div {:class "header"}
   [:div
    [:div {:class "center"}
     [:div {:class "topLinks"}
      [:div {:class "menu-top-links-container"}
       [:ul {:id "menu-top-links" :class "menu"}
;        [:li {:class "menu-item"} [:a {:href "about.html"} "About"]]
        ]]]
     [:h1 {:id "logo"}
      [:img {:src "/images/logo.png" :alt "logo"}]]
     [:div {:class "nav"}
      [:div {:class "menu-main-nav-container"}
       [:ul {:id "menu-main-nav" :class "menu"}
        [:li {:class "menu-item"}
         [:a {:href "/ballots"} [:span "My Ballots"]]
         [:a {:href "/add-ballot"} [:span "Create Ballot"]]
         ]]]]
     [:div {:class "page-title"}
      [:div
       [:h2 title]]]
     ]]])

(defpartial footer []
  [:div {:class "footer"}
    [:div {:class "prom"}
     [:div {:class "center"}
      [:h2 ""] ] ]
    [:div {:class "links"}
     [:div {:class "center"}
      [:div
       [:h3 "Special thanks to"]
       [:p
        [:a {:href "http://clojure.org" } "Clojure"]
        [:br]
        [:a {:href "http://webnoir.org" } "Noir"]
        [:br]
        [:a {:href "https://github.com/gcv/appengine-magic" } "appengine-magic"]]]
      [:div
       [:h3 "Connect with Me" ]
       [:ul {:class "social-links"}
         [:li [:a {:href "http://twitter.com/coopernurse"} [:img {:src "/images/i_socialTwitter.png"}]]]
         [:li [:a {:href "http://linkedin.com/profile/view?id=33660"} [:img {:src "/images/i_socialLinkedin.png"}]]]
         [:br]
         [:li [:a {:href "https://github.com/coopernurse/noirvote"} "See the code at github" ]]
         ]]
      [:div
       [:h3 "Voting stuff" ]
       [:a {:href "http://en.wikipedia.org/wiki/Condorcet_method" } "Condorcet method"]
       [:br]
       [:a {:href "http://en.wikipedia.org/wiki/Ranked_pairs" } "Ranked Pairs"]
       ]]]])

(defpartial layout [title & content]
  (html5
    [:head
     [:title (str "clj-vote - " title)]
     (include-css "/css/saas-common.css" "/css/saas-default.css")
     (include-js  "/js/jquery/jquery.js?ver=1.4.2" "/js/easySlider1.5.js?ver=3.0"
                  "/js/saas.js?ver=3.0" "/js/jquery.cookie.min.js?ver=3.0"
                  "/js/saas.twitter.js?ver=3.0")
    ]
    [:body
     (top-nav title)
     [:div {:class "container"}
      [:div {:class "center"}
       [:div {:class "sidebar right"} ]
;        [:div {:class "widget contacts"}
;         [:h2 "more info"]
;         [:ul
;          [:li "foo"]]]]
       [:div {:class "content"}
        [:div content ]]]]
     (footer)
     ]))
;     [:div [:a {:href "/ballots"} "view ballots"]]
;    ]))

(defpartial error-item [[first-error]]
  [:p.error first-error])

(defpartial form-row [class label field]
  [:div {:class class}
   [:label label]
   [:div {:class "input-box"}
    [:span {:class "wpcf7-form-control-wrap"} field]]])

(defpartial form-row-half-left [label field]
  (form-row "half left" label field))

(defpartial form-row-full [label field]
  (form-row "item" label field))

(defpartial submit-row [label]
  [:div {:class "item a-right"}
   [:div {:class "button"}
    [:span
     [:input {:type "submit" :value label } ]]]])

(defpartial ballot-table-row [b]
  [:tr
   [:td (:name b)]
   [:td [:a {:href (str "/vote/" (:id b))} "vote"]]
   [:td [:a {:href (str "/results/" (:id b))} "results"]]
   [:td [:a {:href (str "/edit-ballot/" (:id b))} "edit"]]
   [:td [:a {:href (str "/delete-ballot/" (:id b))} "delete"]]])

(defpartial ballot-table [ballots]
  [:table {:class "ballots"}
   [:tr [:th "Ballot name"] [:th "Vote"] [:th "View Results"] [:th "Edit"] [:th "Delete"]]
   (map ballot-table-row ballots)])

(defpartial ballot-form-fields [{:keys [name candidates]}]
  (vali/on-error :name error-item)
  (vali/on-error :candidates error-item)
  (form-row-half-left "Ballot Name:" (text-field {:class "radius input-text" :size "40"} "name" name))
  (form-row-full "Candidates (one per line):" (text-area {:class "radius input-text":rows 10 :cols 40 } "candidates" (join-newline candidates))))
  
(defpartial ballot-form [b]
  [:div {:class "contactDiv" }
   [:div
     [:div {:class "wpcf7"}
       (form-to {:class "wpcf7-form"} [:post "/save-ballot"]
         (if b (hidden-field "id" (:id b)))
         (ballot-form-fields b)
         (submit-row "Save Ballot")) ]]])

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
  [:table
   [:tr [:th "Candidate"] [:th "Rating (higher is better)"] ]
   (map vote-candidate-field (iterate inc 0) candidates)])

(defpartial vote-form [b]
  (form-to [:post "/save-vote"]
    (hidden-field "id" (:id b))
    (vote-form-fields b)
    (submit-button "vote!")))

(defpartial results-view-matrix-row [pair]
  (let [matchup (first pair)]
    [:tr
     [:td (str (first matchup) " vs " (second matchup))]
     [:td (second pair)]
     ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; root view funcs ;;
;;;;;;;;;;;;;;;;;;;;;

(defpartial ballots-view [ballots]
  (layout "My Ballots"
      (if (= 0 (count ballots))
        [:div "You have no ballots"]
        (ballot-table ballots))))

(defpartial add-edit-ballot-view [b]
  (layout (if b "Edit Ballot" "New Ballot")
    (ballot-form b)))

(defpartial vote-view [b]
  (layout (str "Vote On: " (:name b))
    [:p "Rate each option (10 = love it, 0 = hate it).  It's ok to rate options the same."]
    (vote-form b)))

(defpartial vote-saved-view [id]
  (layout "Vote Saved!"
    [:h2 "Thanks for voting"]
    [:p [:a {:href (str "/results/" id ) } "View the results" ] ] ))

(defpartial results-view [b winner matrix]
  (layout (str "Results for: " (:name b))
    [:h2 (str "Winner: " winner) ]
    [:p
     [:span "This table shows the set of ranked pairs for this ballot using the "]
     [:a {:href "http://en.wikipedia.org/wiki/Ranked_pairs"} "Ranked Pairs"]
     [:span " method of tabulation."] ]
    [:table
     [:tr [:th "Matchup"] [:th "Victory Margin"] ]
     (map results-view-matrix-row matrix) ]))

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
  (vote-saved-view (:id vote)))

(defpage "/results/:id" {:keys [id]}
  (let [b      (db/get-ballot-by-id id)
        votes  (transform-to-ranked-ballot (db/get-votes-by-ballot-id id))
        matrix (condorcet/tab-ranked-pairs-matrix (:candidates b) votes)
        winner (condorcet/ranked-pairs-winner matrix)]
    (results-view b winner matrix)))
