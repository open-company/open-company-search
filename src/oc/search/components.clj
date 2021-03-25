(ns oc.search.components
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [oc.lib.sentry.core :refer (map->SentryCapturer)]
            [org.httpkit.server :as httpkit]
            [oc.lib.sqs :as sqs]
            [oc.search.elastic-search :as es]))

(defrecord HttpKit [options handler]
  component/Lifecycle
  (start [component]
    (timbre/infof "[http-kit] starting HttpKit with options: %s" options)
    (let [handler (get-in component [:handler :handler] handler)
          server  (httpkit/run-server handler options)]
      (timbre/infof "[http-kit] started HttpKit")
      (assoc component :http-kit server)))
  (stop [{:keys [http-kit] :as component}]
    (timbre/infof "[http-kit] stopping HttpKit")
    (if http-kit
      (do
        (http-kit)
        (timbre/infof "[http-kit] stopped HttpKit")
        (assoc component :http-kit nil))
      component)))

(defrecord Elasticsearch [options handler]
  component/Lifecycle
  (start [component]
    (timbre/info "[elastic-search] starting Elasticsearch...")
    (assoc component :search (es/start)))

  (stop [component]
    (timbre/info "[elastic-search] stopping Elasticsearch...")
    (es/stop)
    (assoc component :search nil)))

(defrecord Handler [handler-fn]
  component/Lifecycle
  (start [component]
    (timbre/info "[handler] starting Handler")
    (assoc component :handler (handler-fn component)))
  (stop [component]
    (assoc component :handler nil)))

(defn search-system [{:keys [port handler-fn sqs-consumer sentry]}]
  (component/system-map
   :sentry-capturer (map->SentryCapturer sentry)
   :elastic-search (component/using
                    (map->Elasticsearch {})
                    [:sentry-capturer])
   :sqs-consumer (component/using
                  (sqs/sqs-listener sqs-consumer)
                  [:sentry-capturer])
   :handler (component/using
             (map->Handler {:handler-fn handler-fn})
             [:elastic-search])
   :server (component/using
            (map->HttpKit {:options {:port port}})
            [:handler])))