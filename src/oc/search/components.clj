(ns oc.search.components
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [clojurewerkz.elastisch.rest :as esr]
            [clojurewerkz.elastisch.rest.index :as idx]
            [oc.search.config :as c]))


(defrecord ElasticSearch [options handler]
  component/Lifecycle
  (start [component]
    (timbre/info "[elastic-search] starting")
    (let [conn (esr/connect c/elastic-search-endpoint)]
      ;; query for index, if not there create
      (when (not (idx/exists? conn c/elastic-search-index))
        (idx/create conn c/elastic-search-index))
      (timbre/info "elastic search started")
      (assoc component :search conn)))

  (stop [{:keys [search] :as component}]
    (dissoc component :search)
    (timbre/info "elastic search stopped")))

(defn search-system [{:keys [port handler-fn] :as opts}]
  (component/system-map :elastic-search (map->ElasticSearch)))
