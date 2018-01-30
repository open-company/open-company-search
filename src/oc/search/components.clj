(ns oc.search.components
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [org.httpkit.server :as httpkit]
            [oc.lib.sqs :as sqs]
            [oc.search.elastic-search :as es]))

(defrecord HttpKit [options handler]
  component/Lifecycle
  (start [component]
    (let [handler (get-in component [:handler :handler] handler)
          server  (httpkit/run-server handler options)]
      (assoc component :http-kit server)))
  (stop [{:keys [http-kit] :as component}]
    (if http-kit
      (do
        (http-kit)
        (dissoc component :http-kit))
      component)))

(defrecord Elasticsearch [options handler]
  component/Lifecycle
  (start [component]
    (timbre/info "[elastic-search] starting Elasticsearch...")
    (assoc component :search (es/start)))

  (stop [component]
    (timbre/info "[elastic-search] stopping Elasticsearch...")
    (es/stop)
    (dissoc component :search)))

(defrecord Handler [handler-fn]
  component/Lifecycle
  (start [component]
    (timbre/info "[handler] starting")
    (assoc component :handler (handler-fn component)))
  (stop [component]
    (dissoc component :handler)))

(defn search-system [{:keys [port handler-fn sqs-consumer]}]
  (component/system-map
   :elastic-search (component/using (map->Elasticsearch {}) [])
   :sqs-consumer (sqs/sqs-listener sqs-consumer)
   :handler (component/using
             (map->Handler {:handler-fn handler-fn})
             [:elastic-search])
   :server (component/using
            (map->HttpKit {:options {:port port}})
            [:handler])))