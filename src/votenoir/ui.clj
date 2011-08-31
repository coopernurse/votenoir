(ns votenoir.ui
  (:use [noir.core :only (defpage defpartial render pre-route)])
  (:use [hiccup.core :only (resolve-uri)])
  (:use [hiccup.page-helpers])
  (:use [hiccup.form-helpers])
  (:require [clojure.contrib.json :as json])
  (:require [clojure.contrib.zip-filter.xml :as zx])
  (:require [clj-appengine-oauth.core :as oauth])
  (:require [noir.validation :as vali])
  (:require [noir.cookies :as cookies])
  (:require [noir.response :as response])
  (:require [noir.session :as session])
  (:require [votenoir.requtil :as requtil])
  (:require [votenoir.db :as db])
  (:require [vote.condorcet :as condorcet])
  (:require [appengine-magic.services.url-fetch :as url-fetch])
  (:require [appengine-magic.services.user :as user]))

(defmacro dbg[x] `(let [x# ~x] (println '~x "=" x#) x#))

(defn split-newline
  [x]
  (map clojure.string/trim (filter #(not (= "" %)) (clojure.string/split x #"\n"))))

(defn join-newline
  [xs]
  (clojure.string/join "\n" xs))

(defn create-uuid []
  (.toString (java.util.UUID/randomUUID)))

(defn create-user-id []
  (let [id (create-uuid)]
    (cookies/put! :user-id-temp id)
    id))

(defn get-create-user-id []
  (cond
    (and (user/user-logged-in?) (not (nil? (.getUserId (user/current-user))))) (.getUserId (user/current-user))
    (not (nil? (session/get :user-id))) (session/get :user-id)
    (nil? (cookies/get :user-id-temp)) (create-user-id)
    :else (cookies/get :user-id-temp)))

(defn ballot-vote-url [b]
  (requtil/absolute-url (str "/vote/" (:id b))))

(defn distinct-param-ids [params]
  (distinct (filter #(not (nil? %)) (map (fn [z] (second (clojure.string/split (str z) #"-"))) (keys params)))))

(defn indexed-param-to-map-pairs [params k v]
  (map (fn [kw] [((keyword (str k kw)) params) ((keyword (str v kw)) params)]) (distinct-param-ids params)))

(defn indexed-param-to-map [params k v]
  (apply array-map (apply concat (indexed-param-to-map-pairs params k v))))

(defn logged-in? []
  (or (user/user-logged-in?) (session/get :user-id)))

(defn user-display-name []
  (if (user/user-logged-in?)
    (user/current-user)
    (session/get :user-display-name)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ui components ;;
;;;;;;;;;;;;;;;;;;;

(defpartial header [title]
  [:div {:class "header"}
   [:div
    [:div {:class "center"}
     [:div {:class "topLinks"}
      [:div {:class "menu-top-links-container"}
       [:ul {:id "menu-top-links" :class "menu"}
        (if (logged-in?)
          (list
          [:li {:class "menu-item"} "Logged in as: " (user-display-name)]
          [:li {:class "menu-item"} [:a {:href "/logout"} "Logout"]]))
        ]]]
     [:h1 {:id "logo"}
      [:a {:href "/"} [:img {:src "/images/logo.png" :alt "logo"}]] ]
     [:div {:class "nav"}
      [:div {:class "menu-main-nav-container"}
       [:ul {:id "menu-main-nav" :class "menu"}
        [:li {:class "menu-item"}
         [:a {:href "/secure/ballots"} [:span "My Ballots"]]
         [:a {:href "/secure/add-ballot"} [:span "Create Ballot"]]
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
       [:h3 "Connect with James" ]
       [:ul {:class "social-links"}
         [:li [:a {:href "http://twitter.com/coopernurse"} [:img {:src "/images/i_socialTwitter.png"}]]]
         [:li [:a {:href "http://linkedin.com/profile/view?id=33660"} [:img {:src "/images/i_socialLinkedin.png"}]]]
         [:br]
         [:li [:a {:href "http://www.bitmechanic.com/"} "my blog" ]]
         [:br]
         [:li [:a {:href "http://bookfriend.me/"} "my nook and kindle book sharing site" ]]
         ]]
      [:div
       [:h3 "Voting stuff" ]
       [:a {:href "http://en.wikipedia.org/wiki/Condorcet_method" } "Condorcet Method"]
       [:br]
       [:a {:href "http://en.wikipedia.org/wiki/Ranked_pairs" } "Ranked Pairs"]
       [:br]
       [:a {:href "https://github.com/coopernurse/votenoir"} "Source code for this site" ]]]]])

(defpartial body [content]
  [:div {:class "container"}
   [:div {:class "center"}
    [:div {:class "sidebar right"} ]
     [:div {:class "content"}
      [:div content ]]]])

(defpartial layout [title & content]
  (html5
    [:head
     [:title (str "votenoir - " title)]
     (include-css "/css/saas-common.css" "/css/saas-default.css")
     (include-js  "/js/jquery/jquery.js?ver=1.4.2"
                  "/js/saas.js?ver=3.0"
                  "/js/jquery.cookie.min.js?ver=3.0"
                  "/js/jquery.socialbutton.js")]
    [:body
     (header title)
     (body content)
     (footer)]))

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

(defpartial ballot-table-row [b can-modify]
  [:tr
   [:td (:name b)]
   [:td [:a {:href (str "/share/" (:id b))} "share"]]
   [:td [:a {:href (str "/vote/" (:id b))} "vote"]]
   [:td [:a {:href (str "/results/" (:id b))} "results"]]
   (if can-modify
    (list [:td [:a {:href (str "/secure/edit-ballot/" (:id b))} "edit"]]
          [:td [:a {:href (str "/secure/delete-ballot/" (:id b))} "delete"]]))])

(defpartial ballot-table [ballots can-modify]
  [:table {:class "ballots"}
   [:tr [:th "Ballot name"] [:th "Share" ] [:th "Vote"] [:th "View Results"]
    (if can-modify (list [:th "Edit"] [:th "Delete"])) ]
   (map (fn [b] (ballot-table-row b can-modify)) ballots)])

(defpartial ballot-form-fields [{:keys [name candidates]}]
  (vali/on-error :name error-item)
  (vali/on-error :candidates error-item)
  (form-row-half-left "Ballot Name:" (text-field {:class "radius input-text" :size "40"} "name" name))
  (form-row-full "Candidates (one per line):" (text-area {:class "radius input-text":rows 10 :cols 40 } "candidates" (join-newline candidates))))

(defpartial wrap-form [action & content]
  [:div {:class "contactDiv" }
   [:div
     [:div {:class "wpcf7"}
       (form-to {:class "wpcf7-form"} [:post action] content)]]])

(defpartial ballot-form [b]
  (wrap-form "/secure/save-ballot"
    (if b (hidden-field "id" (:id b)))
    (ballot-form-fields b)
    (submit-row "Save Ballot")))

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
  (wrap-form "/save-vote"
    (hidden-field "id" (:id b))
    (vote-form-fields b)
    (submit-row "Save Vote")))

(defpartial results-view-matrix-row [pair]
  (let [matchup (first pair)]
    [:tr
     [:td [:b (first matchup) ] (str " beats " (second matchup))]
     [:td (second pair)]
     ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; root view funcs ;;
;;;;;;;;;;;;;;;;;;;;;

(defpartial ballots-view [ballots votes]
  (layout "My Ballots"
    [:h3 "Ballots You've Created"]
    (if (= 0 (count ballots))
      [:div "You have no ballots"]
      (ballot-table ballots true))
    [:h3 "Ballots You've Voted On"]
    (if (= 0 (count votes))
      [:div "You have no votes"]
      (ballot-table votes false))
  ))

(defpartial add-edit-ballot-view [b]
  (layout (if b "Edit Ballot" "New Ballot")
    (ballot-form b)))

(defpartial vote-upsell-view [b]
  (let [url (ballot-vote-url b) ]
    (layout (str "Vote On: " (:name b))
      [:h2 "Want to login first?"]
      [:p (list "Before you vote, you can login with your Google or Gmail account. "
       "This will let you view polls you voted on, create new polls, and edit your vote." ) ]
      [:h3 [:a {:href (user/login-url :destination url )} "Sounds good, let's login"] ]
      [:h3 [:a {:href (str url "?force=1")} "No thanks, just let me vote"] ])))

(defpartial vote-view [b]
  (layout (str "Vote On: " (:name b))
    [:p "Rate each option (10 = love it, 0 = hate it).  It's ok to rate options the same."]
    [:p "If you've already voted on this ballot, this will replace your previous vote."]
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

(defpartial share-view [b]
  (let [url (ballot-vote-url b) ]
    (layout (:name b)
      [:h2 "Invite others to vote"]
      [:p "There are many ways to share your ballot."]
      [:h3 "Email this link:"]
      [:p url]
      [:h3 "Share it on social media sites:" ]
      [:div {:class "share"}
       [:div {:id "facebook-share"}]
       [:div {:id "twitter-share" :class "pad"} ]
       [:div {:id "google-share" :class "pad"} ]]
      (javascript-tag
        (str
          (format "jQuery('#facebook-share').socialbutton('facebook_like', { height: 10, url: '%s'});" url)
          (format "jQuery('#twitter-share').socialbutton('twitter', { button: 'horizontal', url: '%s'});" url)
          (format "jQuery('#google-share').socialbutton('google_plusone', { lang: 'en', href: '%s'});" url)
        )
      ))))

(defpartial login-view []
  (layout "Please Login"
    [:p "We need to know who you are before you can continue." ]
    [:h3 [:a {:href (user/login-url)} "Login with Google"]]
    [:h3 [:a {:href "login-twitter"} "Login with Twitter"]]
    [:h3 [:a {:href "login-facebook"} "Login with Facebook"]]
    [:h3 [:a {:href "login-netflix"} "Login with Netflix"]]
    ))

(defpartial home-view []
  (layout "Make your own polls"
    [:h2 "Welcome!" ]
    [:p (list "This site is a fun way to create a share polls / ballots with your friends and colleagues. "
      "After you create a ballot, you can email the link to vote, or post it to your Facebook or Twitter account.") ]
    [:p "You need a Google or Gmail account to create a ballot, but all polls are open to the public for voting."]
    [:h3 [:a {:href "/secure/add-ballot" } "Create a ballot now"] ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ballot-valid? [{:keys [name candidates]}]
  (vali/rule (vali/min-length? name 3)
             [:name "Name must have at least 3 letters"])
  (vali/rule (vali/has-value? candidates)
             [:candidates "You must have some candidates"])
  (not (vali/errors? :name :candidates)))

(defn save-ballot-success [ballot user-id]
  (let [ballot-id (if (:id ballot) (:id ballot) (create-uuid))]
    (db/put-ballot
      ballot-id
      user-id
      (:name ballot)
      (split-newline (:candidates ballot)))
    (response/redirect (str "/share/" ballot-id))))

(defn save-vote [vote user-id]
  (db/put-vote
    (:id vote)
    user-id
    (indexed-param-to-map vote "key-" "score-")))

(defn transform-to-ranked-ballot [votes]
  (map (fn [m] (zipmap (keys m) (map (fn [v] (Math/abs (- 10 (Integer/parseInt v)))) (vals m)))) votes))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(pre-route "/secure/*" {}
  (when-not (logged-in?)
   (response/redirect "/login")))

(pre-route "/admin/*" {}
  (if-not (logged-in?)
    (response/redirect "/login")
    (if-not (user/user-admin?)
      (response/redirect "/"))))

(defpage "/" {:as req}
  (home-view))

(defpage "/login" {:as req}
  (login-view))

(defpage "/logout" {:as req}
  (session/remove! :user-id)
  (session/remove! :user-display-name)
  (response/redirect (user/logout-url)))

(defpage "/secure/ballots" {:as req}
  (ballots-view
    (db/get-ballots-by-user (get-create-user-id))
    (db/get-ballots-voted-on-by-user-id (get-create-user-id))))

(defpage "/secure/add-ballot" {:as req}
  (add-edit-ballot-view nil))

(defpage "/secure/edit-ballot/:id" {:keys [id]}
  (add-edit-ballot-view (db/get-ballot-by-id id)))

(defpage "/secure/delete-ballot/:id" {:keys [id]}
  (db/delete-ballot-by-id id (get-create-user-id))
  (render "/secure/ballots" nil))

(defpage [:post "/secure/save-ballot"] {:as ballot}
  (if (ballot-valid? ballot)
    (save-ballot-success ballot (get-create-user-id))
    (render "/secure/add-ballot" ballot)))

(defpage "/vote/:id" {:keys [id force]}
  (let [b (db/get-ballot-by-id id)]
    (if (or (user/user-logged-in?) (= "1" force))
      (vote-view b)
      (vote-upsell-view b))))

(defpage [:post "/save-vote"] {:as vote}
  (save-vote vote (get-create-user-id))
  (vote-saved-view (:id vote)))

(defpage "/share/:id" {:keys [id]}
  (share-view (db/get-ballot-by-id id)))

(defpage "/results/:id" {:keys [id]}
  (let [b      (db/get-ballot-by-id id)
        votes  (transform-to-ranked-ballot (db/get-votes-by-ballot-id id))
        matrix (condorcet/tab-ranked-pairs-matrix (:candidates b) votes)
        winner (condorcet/ranked-pairs-winner matrix)]
    (results-view b winner matrix)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; oauth ;;
;;;;;;;;;;;

(defn make-oauth-consumer [provider]
  (let [conf (db/get-all-config-as-map)]
    (oauth/make-consumer (conf (str provider ".key")) (conf (str provider ".secret")))))

(defpage "/login-twitter" {:as req}
  (let [consumer (make-oauth-consumer "twitter")]
    (session/put! :oauth-consumer consumer)
    (response/redirect
      (oauth/get-authorize-url
        (oauth/make-provider-twitter)
        consumer
        (requtil/absolute-url "/oauth-callback-twitter")))))

(defpage "/login-netflix" {:as req}
  (let [consumer (make-oauth-consumer "netflix")]
    (session/put! :oauth-consumer consumer)
    (response/redirect
      (oauth/get-authorize-url
        (oauth/make-provider-netflix consumer "votenoir")
        consumer
        (requtil/absolute-url "/oauth-callback-netflix")))))

(defpage "/login-facebook" {:as req}
  (let [consumer (make-oauth-consumer "facebook")]
    (session/put! :oauth-consumer consumer)
    (response/redirect
      (oauth/get-authorize-url-facebook
        consumer
        (requtil/absolute-url "/oauth-callback-facebook")))))

(defn set-logged-in-and-redirect
  [user-json provider-name id-key name-key]
  (let [user-map (json/read-json user-json)]
    (session/put! :user-id (str provider-name "-" (id-key user-map)))
    (session/put! :user-display-name (name-key user-map))
    (response/redirect (requtil/absolute-url "/secure/ballots"))))

(defpage "/oauth-callback-twitter" {:as req}
  "We get :oauth_verifier and :oauth_token from the oauth provider
  on this request.  stash the token in the session so we can make calls
  on behalf of this user"
  (let [    consumer (session/get :oauth-consumer)
        access-token (oauth/get-access-token
                       (oauth/make-provider-twitter) consumer (:oauth_verifier req))
           user-json (oauth/get-protected-url
                       consumer
                       access-token
                       "http://api.twitter.com/1/account/verify_credentials.json"
                       "utf-8")]
    (set-logged-in-and-redirect user-json "twitter" :id :name)))

(defn parse-netflix-href [s]
  (let [xp (clojure.xml/parse (java.io.ByteArrayInputStream. (.getBytes s)))]
    (:href (:attrs (first (first (zx/xml-> (clojure.zip/xml-zip xp) :link)))))))

(defn xml-first-occur [xz kw]
  (first (:content (first (first (clojure.contrib.zip-filter.xml/xml-> xz kw))))))

(defpage "/oauth-callback-netflix" {:as req}
  (let [    consumer (session/get :oauth-consumer)
        access-token (oauth/get-access-token
                       (oauth/make-provider-netflix consumer "votenoir")
                       consumer
                       (:oauth_verifier req))
           curr-xml  (oauth/get-protected-url
                       consumer
                       access-token
                       "http://api.netflix.com/users/current"
                       "utf-8")
           user-xml  (oauth/get-protected-url
                       consumer
                       access-token
                       (parse-netflix-href curr-xml)
                       "utf-8")
           user-zip  (clojure.zip/xml-zip
                       (clojure.xml/parse
                         (java.io.ByteArrayInputStream. (.getBytes user-xml))))]
    (session/put! :user-id (str "netflix-" (xml-first-occur user-zip :user_id)))
    (session/put! :user-display-name
      (str (xml-first-occur user-zip :first_name) " "
        (xml-first-occur user-zip :last_name)))
    (response/redirect (requtil/absolute-url "/secure/ballots"))))

(defpage "/oauth-callback-facebook" {:keys [code]}
  "We get :oauth_verifier and :oauth_token from the oauth provider
  on this request.  stash the token in the session so we can make calls
  on behalf of this user"
  (let [    consumer (session/get :oauth-consumer)
        access-token (oauth/get-access-token-facebook consumer
                        code (requtil/absolute-url "/oauth-callback-facebook"))
           user-json (oauth/get-protected-url-facebook
                       (:access_token access-token) "https://graph.facebook.com/me" "utf-8")]
    (set-logged-in-and-redirect user-json "facebook" :id :name)))


