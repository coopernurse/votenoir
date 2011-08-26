(ns votenoir.db
  (:require [appengine-magic.services.datastore :as ds]))

;; candidates is a
(ds/defentity ballot-entity [^:key id, user-id, name, candidates, modified])

;; id is: ballot-id + user-id
(ds/defentity vote-entity [^:key id, ballot-id, user-id, scores, modified])

(defn entity-to-map
  [e]
  (if e
    (zipmap (keys e) (vals e))
    nil))

(defn get-ballots-by-user
  "Returns a list of ballots by userid
  input: userid (string)
  output: seq of maps (:id :name :candidates)"
  [user-id]
  (map entity-to-map (ds/query :kind ballot-entity :filter (= :user-id user-id) :sort [[:modified :desc]])))

(defn get-ballot-by-id
  "Returns a single ballot by id
  input: ballot id (string)
  ouput: map"
  [ballot-id]
  (entity-to-map (ds/retrieve ballot-entity ballot-id)))

(defn delete-ballot
  [b]
  (ds/delete! b)
  (ds/delete! (ds/query :kind vote-entity :filter (= :ballot-id (:id b)))))

(defn delete-ballot-by-id
  "Deletes a ballot and its votes by id.  Ensures userid owns ballotid"
  [ballot-id user-id]
  (let [b (ds/retrieve ballot-entity ballot-id)]
    (if b
      (if (= user-id (:user-id b)) (delete-ballot b)))))

(defn put-ballot
  "Saves ballot, replacing any data previously there.  Does not delete votes."
  [ballot-id user-id name candidates]
  (ds/save! (ballot-entity. ballot-id user-id name candidates (System/currentTimeMillis))))

(defn get-votes-by-ballot-id
  "Returns seq of maps
  input: ballot id (string)
  output: seq of maps (key=candidate id, value=score)"
  [ballot-id]
  (map (fn [v] (read-string (:scores v))) (ds/query :kind vote-entity :filter (= :ballot-id ballot-id))))

(defn get-votes-by-user-id
  "Returns seq of maps
  input: user id (string)
  output: seq of maps (keys in map: :user-id, :ballot-id, :scores)"
  [user-id]
  (map entity-to-map
    (ds/query :kind vote-entity :filter (= :user-id user-id) :sort [[:modified :desc]])))

(defn get-ballots-voted-on-by-user-id
  [user-id]
  (map (fn [v] (let [b (get-ballot-by-id (:ballot-id v))] (assoc v :name (:name b) :id (:id b)))) (get-votes-by-user-id user-id)))

(defn put-vote
  "Saves vote for a single person, replacing the previous vote for that person
  input: ballot id (string), user id (string), map (key=candidate id, value=score)"
  [ballot-id user-id scores]
  (ds/save! (vote-entity. (str ballot-id "-" user-id) ballot-id user-id (pr-str scores) (System/currentTimeMillis))))
