(ns oc.search.api
  "API to search data in Elasticsearch."
  (:require [compojure.core :as compojure :refer (OPTIONS GET)]
            [taoensso.timbre :as timbre]
            [cheshire.core :as json]
            [liberator.core :refer (defresource by-method)]
            [oc.lib.api.common :as api-common]
            [oc.search.config :as config]
            [oc.search.elastic-search :as esearch]))

(defonce search-media-type "application/vnd.open-company.search.v1+json")

(defn- handle-search
  [params ctx]
  (timbre/debug "Searching...")
  (let [params_teams (assoc params :teams (:teams (:user ctx)))
        params_user (assoc params_teams :uuid (:user-id (:user ctx)))
        result (esearch/search params_user)]
    (timbre/debug "Search Result:" result)
    (json/generate-string
     (:hits result)
     {:pretty config/pretty?})))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

(defresource search [params]
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
  :processable? true

  ;; Responses
  :handle-ok (fn [ctx] (handle-search params ctx))
  :handle-no-content (fn [ctx] (when-not (:existing? ctx) (api-common/missing-response))))

;; ----- Routes -----

(defn routes [sys]
  (compojure/routes
   (OPTIONS "/search/" {params :query-params} (search params))
   (OPTIONS "/search" {params :query-params} (search params))
   (GET "/search/" {params :query-params} (search params))
   (GET "/search" {params :query-params} (search params))))