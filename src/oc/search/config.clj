(ns oc.search.config
  "Namespace for the configuration parameters."
  (:require [environ.core :refer (env)]))

(defn- bool
  "Handle the fact that we may have true/false strings, when we want booleans."
  [val]
  (boolean (Boolean/valueOf val)))

;; ----- System -----

(defonce prod? (= "production" (env :env)))
(defonce intro? (bool (or (env :intro ) false)))

;; ----- Logging -----

(defonce log-level (or (env :log-level) :info))

;; ----- Sentry -----

(defonce dsn (or (env :sentry-dsn) false))

;; ----- AWS -----

(defonce aws-access-key-id (env :aws-access-key-id))
(defonce aws-secret-access-key (env :aws-secret-access-key))
(defonce aws-creds {:access-key aws-access-key-id
                    :secret-key aws-secret-access-key})
(defonce aws-endpoint (env :aws-endpoint))

;; ----- AWS SQS -----

(defonce aws-sqs-search-index-queue (env :aws-sqs-search-index-queue))

;; ----- Elastic Search -----
(defonce elastic-search-endpoint (env :elastic-search-endpoint))
(defonce elastic-search-index (env :elastic-search-index))

;; ----- HTTP server -----

(defonce hot-reload (bool (or (env :hot-reload) false)))
(defonce search-server-port (Integer/parseInt (or (env :port) "3007")))

;; ----- Liberator -----

;; see header response, or http://localhost:3000/x-liberator/requests/ for trace results
(defonce liberator-trace (bool (or (env :liberator-trace) false)))
(defonce pretty? (not prod?)) ; JSON response as pretty?

;; ----- JWT -----

(defonce passphrase (env :open-company-auth-passphrase))
