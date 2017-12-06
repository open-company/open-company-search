(ns oc.search.core
  (:gen-class)
  (:require [taoensso.timbre :as timbre]))


(defn entry-index
  [entity-data]
  (timbre/debug "received entry to index.")
  (timbre/debug entity-data)
  )
