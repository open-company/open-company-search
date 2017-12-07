(ns oc.search.core
  (:gen-class)
  (:require [taoensso.timbre :as timbre]
            [oc.search.elastic-search :as es]))


(defn entry-index
  [entity-data]
  (timbre/debug "received entry to index.")
  (es/add-index entity-data))
