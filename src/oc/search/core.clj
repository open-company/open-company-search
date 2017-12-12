(ns oc.search.core
  (:gen-class)
  (:require [taoensso.timbre :as timbre]
            [oc.search.elastic-search :as es]))


(defn index-entry
  [entry-data]
  (timbre/debug "received entry to index.")
  (es/add-index entry-data))

(defn delete-entry
  [uuid]
  (timbre/debug "received entry to index.")
  (es/delete uuid))