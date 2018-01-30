(ns oc.search.app
  (:gen-class)
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [ring.middleware.keyword-params :refer (wrap-keyword-params)]
            [ring.middleware.params :refer (wrap-params)]
            [ring.middleware.reload :refer (wrap-reload)]
            [ring.middleware.cors :refer (wrap-cors)]
            [ring.logger.timbre :refer (wrap-with-logger)]
            [cheshire.core :as json]
            [compojure.core :as compojure :refer (GET)]
            [liberator.dev :refer (wrap-trace)]
            [raven-clj.ring :as sentry-mw]
            [oc.search.config :as c]
            [oc.lib.sentry-appender :as sentry]
            [oc.lib.sqs :as sqs]
            [oc.search.components :as components]
            [oc.search.core :as ocsearch]
            [oc.search.api :as search-api]))

(defn sqs-handler [msg done-channel]
  (let [msg-sqs-body (json/parse-string (:body msg) true)
        msg-body (json/parse-string (:Message msg-sqs-body) true)
        msg-type (str (:resource-type msg-body) "-" (if (= (:notification-type msg-body) "delete") "delete" "index"))]
    (timbre/info "Received message from SQS.")
    (timbre/debug "Message from SQS:" msg-body)
    (when (:test-error msg-body)
      (/ 1 0)) ; test Sentry error reporting
    (case msg-type
      "entry-index" (ocsearch/index-entry msg-body)
      "entry-delete" (ocsearch/delete-entry msg-body)
      "board-index" (ocsearch/index-board msg-body)
      "board-delete" (ocsearch/delete-board msg-body)
      (timbre/info "Unrecognized message type" msg-body)))
  (sqs/ack done-channel msg))

;; ----- Request Routing -----

(defn routes [sys]
  (compojure/routes
    (GET "/ping" [] {:body "OpenCompany Search Service: OK" :status 200}) ; Up-time monitor
    (GET "/---error-test---" [] (/ 1 0))
    (GET "/---500-test---" [] {:body "Testing bad things." :status 500})
    (search-api/routes sys)))

(defn echo-config [port]
  (println (str "\n"
    "Running on port: " port "\n"
    "Elasticsearch endpoint: " c/elastic-search-endpoint "\n"
    "Elasticsearch index:" c/elastic-search-index "\n"
    "AWS SQS queue: " c/aws-sqs-search-index-queue "\n"
    "Hot-reload: " c/hot-reload "\n"
    "Trace: " c/liberator-trace "\n"
    "Sentry: " c/dsn "\n\n"
    (when c/intro? "Ready to serve...\n"))))

(defn app
  "Ring app definition"
  [sys]
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

  ;; Start the system
  (-> {:handler-fn app
       :port port
       :sqs-consumer
       {:sqs-queue c/aws-sqs-search-index-queue
        :message-handler sqs-handler
        :sqs-creds {:access-key c/aws-access-key-id
                    :secret-key c/aws-secret-access-key}}}
      components/search-system
      component/start)

  ;; Echo config information
  (println (str "\n"
    (when c/intro? (str (slurp (clojure.java.io/resource "oc/assets/ascii_art.txt")) "\n"))
    "OpenCompany Search Service\n"))
  (echo-config port))

(defn -main []
  (start c/search-server-port))