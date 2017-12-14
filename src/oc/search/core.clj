(ns oc.search.core
  (:gen-class)
  (:require [taoensso.timbre :as timbre]
            [oc.search.elastic-search :as es]))


(defn index-entry
  [entry-data]
  (timbre/debug "received entry to index.")
  (es/add-entry-index entry-data))

(defn delete-entry
  [data]
  (timbre/debug "received entry to delete.")
  (es/delete-entry data))


(defn index-board
  [board-data]
  (timbre/debug "received board to index.")
  (es/add-board-index board-data))


(defn delete-board
  [board-data]
  (timbre/debug "received board to delete.")
  (es/delete-board board-data))