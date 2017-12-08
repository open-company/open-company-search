(ns oc.search.core
  (:gen-class)
  (:require [taoensso.timbre :as timbre]
            [oc.search.elastic-search :as es]))


(defn entry-index
  [entry-data]
  (timbre/debug "received entry to index.")
  (es/add-index entry-data))
