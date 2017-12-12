(ns oc.search.app
  (:gen-class)
  (:require [com.stuartsierra.component :as component]
            [manifold.stream :as stream]
            [taoensso.timbre :as timbre]
            [ring.middleware.json :refer (wrap-json-body)]
            [ring.middleware.keyword-params :refer (wrap-keyword-params)]
            [ring.middleware.params :refer (wrap-params)]
            [ring.middleware.reload :refer (wrap-reload)]
            [ring.middleware.cors :refer (wrap-cors)]
            [ring.logger.timbre :refer (wrap-with-logger)]
            [cheshire.core :as json]
            [compojure.core :as compojure :refer (GET)]
            [liberator.dev :refer (wrap-trace)]
            [raven-clj.interfaces :as sentry-interfaces]
            [raven-clj.ring :as sentry-mw]
            [oc.search.config :as c]
            [oc.lib.sentry-appender :as sentry]
            [oc.lib.sqs :as sqs]
            [oc.search.components :as components]
            [oc.search.core :as ocsearch]
            [oc.search.api :as search-api]))

(defn sqs-handler [msg done-channel]
  (let [msg-body (json/parse-string (:body msg) true)
        msg-type (:resource-type msg-body)
        error (if (:test-error msg-body) (/ 1 0) false)] ; test Sentry error reporting
    (timbre/info "Received message from SQS.")
    (timbre/tracef "\nMessage from SQS: %s\n" msg-body)
    (case msg-type
      "entry-index" (ocsearch/index-entry msg-body)
      "delete-index" (ocsearch/delete-entry msg-body)
      (timbre/error "Unrecognized message type" msg-type)))
  (sqs/ack done-channel msg))

(defn system [config-options]
  (let [{:keys [sqs-creds sqs-queue sqs-msg-handler]} config-options]
    (component/system-map
      :sqs (sqs/sqs-listener sqs-creds sqs-queue sqs-msg-handler))))

;; ----- Request Routing -----

(defn routes [sys]
  (compojure/routes
    (GET "/ping" [] {:body "OpenCompany Search Service: OK" :status 200}) ; Up-time monitor
    (GET "/---error-test---" [] (/ 1 0))
    (GET "/---500-test---" [] {:body "Testing bad things." :status 500})
    (search-api/routes sys)))

(defn echo-config []
  (println (str "\n"
    "AWS SQS queue: " c/aws-sqs-search-index-queue "\n"
    "Sentry: " c/dsn "\n"
    "Elastic Search Endpoint: " c/elastic-search-endpoint "\n"
    "Elastic Search Index:" c/elastic-search-index "\n\n"
    (when c/intro? "Ready to serve...\n"))))

;; Ring app definition
(defn app [sys]
  (cond-> (routes sys)
    c/dsn             (sentry-mw/wrap-sentry c/dsn) ; important that this is first
    c/prod?           wrap-with-logger
    true              wrap-keyword-params
    true              wrap-params
    c/liberator-trace (wrap-trace :header :ui)
    true              (wrap-cors #".*")
    c/hot-reload      wrap-reload))

(defn start [port]

  ;; Log errors to Sentry
  (if c/dsn
    (timbre/merge-config!
      {:level (keyword c/log-level)
       :appenders {:sentry (sentry/sentry-appender c/dsn)}})
    (timbre/merge-config! {:level (keyword c/log-level)}))

  ;; Uncaught exceptions go to Sentry
  (Thread/setDefaultUncaughtExceptionHandler
   (reify Thread$UncaughtExceptionHandler
     (uncaughtException [_ thread ex]
       (timbre/error ex "Uncaught exception on" (.getName thread) (.getMessage ex)))))

  ;; Echo config information
  (println (str "\n"
    (when c/intro? (str (slurp (clojure.java.io/resource "oc/assets/ascii_art.txt")) "\n"))
    "OpenCompany Search Service\n"))
  (echo-config)

  ;;(component/start (components/search-system)))
  (timbre/debug (components/search-system))
  ;; Start the system, which will start long polling SQS
  (component/start (system {:handler-fn app
                            :port port
                            :sqs-queue c/aws-sqs-search-index-queue
                            :sqs-msg-handler sqs-handler
                            :sqs-creds {:access-key c/aws-access-key-id
                                        :secret-key c/aws-secret-access-key}}))

  (deref (stream/take! (stream/stream)))) ; block forever

(defn -main []
  (start))
