(ns oc.search.api
  "API to search data in elastic search."
  (:require [compojure.core :as compojure :refer (GET)]
            [taoensso.timbre :as timbre]
            [liberator.core :refer (defresource by-method)]
            [oc.lib.api.common :as api-common]
            [oc.search.config :as config]))

(defonce search-media-type "application/vnd.open-company.search.v1+json")

(defn start [])

(defn stop [])


(defresource search []
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken
  :allowed-methods [:options :get]

  ;; Media type client accepts
  :available-media-types [search-media-type]
  :handle-not-acceptable (api-common/only-accept 406 search-media-type)

  ;; Media type client sends
  :known-content-type? (by-method {
    :options true
    :get (fn [ctx] (api-common/known-content-type? ctx search-media-type))})

  ;; Authorization
  :allowed? true

  ;; Validations
  :processable? (by-method {
    :options true
    :get true})

  ;; Responses
  :handle-ok (fn [ctx]
               (timbre/debug "Searching")
               ctx)
  :handle-no-content (fn [ctx] (when-not (:existing? ctx) (api-common/missing-response))))

;; ----- Routes -----

(defn routes [sys]
  (compojure/routes
   ;; Comment listing and creation
   (GET "/search/" []
        (search))))
