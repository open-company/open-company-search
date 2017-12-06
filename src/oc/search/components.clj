(ns oc.search.components
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [oc.lib.sqs :as sqs]
            [oc.search.elastic-search :as es]
            [oc.search.config :as c]))


(defrecord ElasticSearch [options handler]
  component/Lifecycle
  (start [component]
    (timbre/info "[elastic-search] starting")
    (assoc component :search (es/start)))

  (stop [{:keys [search] :as component}]
    (timbre/info "elastic search stopped")
    (es/stop)
    (dissoc component :search)))

(defn search-system [{:keys [sqs-consumer]}]
  (component/system-map
   :elastic-search (component/using (map->ElasticSearch {}) [])
   :sqs-consumer (sqs/sqs-listener sqs-consumer)))
