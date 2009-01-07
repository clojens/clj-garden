(ns updemo.app
  (:use
    (ring controller request)
    (clj-html core helpers helpers-ext)
    clojure.contrib.str-utils)
  (:require
    [ring.routing                      :as routing]
    [cwsg.middleware.reloading         :as reloading]
    [cwsg.middleware.show-exceptions   :as show-exceptions]
    [cwsg.middleware.file-content-info :as file-content-info]
    [cwsg.middleware.static            :as static]
    [ring.app                          :as app]
    [stash.core                        :as stash]
    [updemo.file-utils                 :as file-utils]))

;; Config
(def app-host "http://localhost:8080")
(def public-dir  (java.io.File. "public"))
(def uploads-dir (java.io.File. "public/uploads"))
(def reloadable-namespace-syms '(updemo.app))
(def data-source
  (doto (org.postgresql.ds.PGPoolingDataSource.)
    (.setDatabaseName "updemo_dev")
    (.setUser         "mmcgrana")
    (.setPassword     "")))

;; Routing
(routing/defrouting
  app-host
  [['updemo.app 'index     :index     :get "/"]
   ['updemo.app 'new       :new       :get "/new"]
   ['updemo.app 'create    :create    :put "/"]
   ['updemo.app 'show      :show      :get "/:id"]
   ['updemo.app 'not-found :not-found :any "/:path" {:path ".*"}]])

;; Models
(stash/defmodel +upload+
  {:data-source data-source
   :table-name :uploads
   :pk-init stash/a-uuid
   :columns
     [[:id            :uuid     {:pk true}]
      [:filename      :string]
      [:content_type  :string]
      [:size          :integer]]})

(defn upload-file [upload]
  (file-utils/file uploads-dir (:id upload)))

(defn normalize-filename [filename]
  (re-gsub #"(?i)[^a-z0-9_.]" "_" filename))

(defn create-upload [upload-map]
  (let [upload (stash/create* +upload+
                 {:filename     (normalize-filename (:filename upload-map))
                  :content_type (:content-type upload-map)
                  :size         (:size upload-map)})]
    (file-utils/cp (:tempfile upload-map) (upload-file upload))))


;; Views
(defmacro with-layout
  [& body]
  `(html
     (doctype :xhtml-transitional)
     [:html {:xmlns "http://www.w3.org/1999/xhtml"}
       [:head
         [:title "ring upload demo"]]
       [:body ~@body]]))

(defn index-view [uploads]
  (with-layout
    [:p [:a {:href (path :new)} "new upload"]]
    [:h3 "Uploaded"]
    (domap-str [upload uploads]
      (html [:p [:a {:href (path :show upload)} (h (:filename upload))]]))))

(defn new-view []
  (with-layout
    (form-to (path-info :create) {:multipart true}
      (html
        [:p "Select file:"]
        (file-field-tag "upload")
        (submit-tag "Upload")))))

;; Controllers
(defn not-found [req]
  (redirect (path :index)))

(defn index [req]
  (respond (index-view (stash/find-all +upload+))))

(defn new [req]
  (respond (new-view)))

(defn create [req]
  (create-upload (params req :upload))
  (redirect (path :index)))

(defn show [req]
  (if-let [upload (stash/find-one +upload+ {:where [:id := (params req :id)]})]
    (send-file (upload-file upload) {:filename (:filename upload)})
    (not-found [req])))

;; CWSG app
(def app
  (show-exceptions/wrap
    (file-content-info/wrap
      (static/wrap public-dir
        (reloading/wrap reloadable-namespace-syms
          (app/spawn-app router))))))