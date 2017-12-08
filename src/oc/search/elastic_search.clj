(ns oc.search.elastic-search
   (:require [taoensso.timbre :as timbre]
             [slingshot.slingshot :as slingshot]
             [clojure.walk :refer (keywordize-keys)]
             [clojurewerkz.elastisch.rest :as esr]
             [clojurewerkz.elastisch.rest.index :as idx]
             [clojurewerkz.elastisch.rest.document :as doc]
             [oc.search.config :as c]))


(defonce mapping-types
  {:entry {:properties {:org-slug     {:type "text" :store "yes" :index false}
                        :org-name     {:type "text" :store "yes" :index false}
                        :headline     {:type "text" :analyzer "snowball"}
                        :author-url   {:type "text" }
                        :author-name  {:type "text" }
                        :author-id    {:type "text"}
                        :secure-uuid  {:type "text"}
                        :uuid         {:type "text"}
                        :status       {:type "text"}
                        :updated-at   {:type "date"}
                        :published-at {:type "date"}
                        :shared-at    {:type "date"}
                        :created-at   {:type "date"}
                        :body         {:type "text" :analyzer "snowball"
                                       :term_vector "with_positions_offsets"}}
           }})

(defn- create-index
  [conn index-name opts]
  (let [{:keys [settings mappings]} opts]
    (esr/put conn (esr/index-url conn index-name)
             {:body (if mappings
                      {:settings settings :mappings mappings}
                      {:settings settings})})))

(defn start []
  (let [conn (esr/connect c/elastic-search-endpoint)
        index (str c/elastic-search-index)]
    (timbre/debug "connected...does index exist?" index)
    ;; query for index, if not there create
    (let [exists? (idx/exists? conn index)
          request-exists? (esr/head conn (esr/index-url conn index))
          mapping (idx/get-mapping conn index)]
      (timbre/debug exists? request-exists? mapping)
      (when (not exists?)
        (timbre/debug "index does not exist...creating.")
        (slingshot/try+
         (timbre/debug (create-index conn index {:mappings mapping-types :settings {}}))
         (catch [:status 400] {:keys [request-time headers body]}
           (timbre/error request-time headers body)))))
    conn))

(defn stop [])


(defn- map-entry
  [entry]
  {:org-slug (:org-slug entry)
   :org-name (:org-name entry)
   :author-id (:user-id (:author entry))
   :author-name (:name (:author entry))
   :author-url (:avatar-url (:author entry))
   :headline (:headline entry)
   :secure-uuid (:secure-uuid entry)
   :uuid (:uuid entry)
   :status (:status entry)
   :updated-at (:updated-at entry)
   :published-at (:published-at entry)
   :shared-at (:shared-at entry)
   :created-at (:created-at entry)
   :body (:body entry)})

(defn add-index
  [entry-data]
  (let [conn (esr/connect c/elastic-search-endpoint)
        index (str c/elastic-search-index)
        entry (:entry entry-data)]
    (timbre/debug entry-data)
    (timbre/info
     (doc/upsert conn index "entry" (:uuid entry) (map-entry entry)))))