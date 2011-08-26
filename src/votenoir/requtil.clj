(ns votenoir.requtil)

(declare *request*)

(defn absolute-url
  "Converts uri into a full URL based on the current request
  For example, given a current request URL of:
  http://example.com:9000/foo/bar
  Deployed as foo.war (so /foo is the servlet context path)
  Then: (absolute-url \"/baz\") returns: http://example.com:9000/foo/baz"
  ([uri]
    (let [req (:request *request*)]
      (absolute-url (.getScheme req) (.getServerName req) (.getServerPort req) (.getContextPath req) uri)))
  ([scheme host port context uri]
      (str scheme "://" host (if (or (= 80 port) (= 443 port)) "" (str ":" port)) context uri)))

(defn wrap-requtil
  [handler]
  (fn [request]
    (binding [*request* request]
      (handler request))))