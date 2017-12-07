(ns oc.search.app
  (:gen-class)
  (:require [com.stuartsierra.component :as component]
            [manifold.stream :as stream]
            [taoensso.timbre :as timbre]
            [oc.search.config :as c]
            [oc.lib.sentry-appender :as sentry]
            [oc.lib.sqs :as sqs]
            [oc.search.components :as components]
            [oc.search.core :as ocsearch]))

(defn sqs-handler [msg done-channel]
  (let [msg-body (read-string (:body msg))
        msg-type (:type msg-body)
        error (if (:test-error msg-body) (/ 1 0) false)] ; test Sentry error reporting
    (timbre/info "Received message from SQS.")
    (timbre/tracef "\nMessage from SQS: %s\n" msg-body)
    (case msg-type
      "entry-index" (ocsearch/entry-index msg-body)
      (timbre/error "Unrecognized message type" msg-type)))
  (sqs/ack done-channel msg))

(defn system [config-options]
  (let [{:keys [sqs-creds sqs-queue sqs-msg-handler]} config-options]
    (component/system-map
      :sqs (sqs/sqs-listener sqs-creds sqs-queue sqs-msg-handler))))

(defn echo-config []
  (println (str "\n"
    "AWS SQS queue: " c/aws-sqs-search-index-queue "\n"
    "Sentry: " c/dsn "\n"
    "Elastic Search Endpoint: " c/elastic-search-endpoint "\n"
    "Elastic Search Index:" c/elastic-search-index "\n\n"
    (when c/intro? "Ready to serve...\n"))))

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
  (component/start (system {:sqs-queue c/aws-sqs-search-index-queue
                           :sqs-msg-handler sqs-handler
                           :sqs-creds {:access-key c/aws-access-key-id
                                       :secret-key c/aws-secret-access-key}}))

  (deref (stream/take! (stream/stream)))) ; block forever

(defn -main []
  (start))

(comment

  ;; SQS message payload
  (def entry (json/decode (slurp "./opt/samples/updates/green-labs.json")))
  (def message 
    {:type "entry-index"
     :org-slug "green-labs"
     :entry entry})

  (require '[amazonica.aws.sqs :as sqs2])
  
  ;; send a test SQS message
  (sqs2/send-message
     {:access-key c/aws-access-key-id
      :secret-key c/aws-secret-access-key}
    c/aws-sqs-search-index-queue
    message)

  ;; send a test message that will cause an exception
  (sqs2/send-message 
     {:access-key c/aws-access-key-id
      :secret-key c/aws-secret-access-key}
    c/aws-sqs-search-index-queue
    {:test-error true})
  )