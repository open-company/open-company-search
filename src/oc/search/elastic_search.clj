(ns oc.search.elastic-search
   (:require [taoensso.timbre :as timbre]
             [slingshot.slingshot :as slingshot]
             [clojure.walk :refer (keywordize-keys)]
             [clojurewerkz.elastisch.rest :as esr]
             [clojurewerkz.elastisch.rest.index :as idx]
             [clojurewerkz.elastisch.rest.document :as doc]
             [clojurewerkz.elastisch.query :as q]
             [oc.search.config :as c]))


(defonce mapping-types
  {:entry {:properties {:org-slug      {:type "text" :store "yes" :index false}
                        :org-name      {:type "text" :store "yes" :index false}
                        :org-uuid      {:type "text"}
                        :org-team-uuid {:type "text"}
                        :board-uuid    {:type "text"}
                        :board-name    {:type "text"}
                        :board-slug    {:type "text"}
                        :headline      {:type "text" :analyzer "snowball"}
                        :author-url    {:type "text" }
                        :author-name   {:type "text" }
                        :author-id     {:type "text"}
                        :secure-uuid   {:type "text"}
                        :uuid          {:type "text"}
                        :status        {:type "text"}
                        :updated-at    {:type "date"}
                        :published-at  {:type "date"}
                        :shared-at     {:type "date"}
                        :created-at    {:type "date"}
                        :body          {:type "text" :analyzer "snowball"
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
  [entry-data]
  (let [entry (:entry entry-data)]
    {:org-slug (:org-slug entry-data)
     :org-name (:org-name entry-data)
     :org-uuid (:org-uuid entry-data)
     :org-team-id (:org-team-id entry-data)
     :board-uuid (:board-uuid entry)
     :board-name (:board-name entry-data)
     :board-slug (:board-slug entry-data)
     :author-id (:user-id (last (:author entry)))
     :author-name (:name (last (:author entry)))
     :author-url (:avatar-url (last (:author entry)))
     :headline (:headline entry)
     :secure-uuid (:secure-uuid entry)
     :uuid (:uuid entry)
     :status (:status entry)
     :updated-at (:updated-at entry)
     :published-at (:published-at entry)
     :shared-at (:shared-at (last (:shared entry)))
     :created-at (:created-at entry)
     :body (:body entry)}))

;; Upsert
(defn add-index
  [entry-data]
  (let [conn (esr/connect c/elastic-search-endpoint)
        index (str c/elastic-search-index)]
    (timbre/debug entry-data)
    (timbre/debug (map-entry entry-data))
    (timbre/info
     (doc/upsert conn index "entry" (:uuid (:entry entry-data)) (map-entry entry-data)))))

;; Search
(defn- filter-by-team
  [teams]
  (q/bool {:filter (vec (map (fn [team] {:term {:org-team-id.keyword team}}) teams))}))

(defn- add-to-query
  [query query-type search-term field value]
  (if value
    (let [search-query (query-type (:bool query))
          adding {search-term {field value}}
          new-filter (if search-query
                       (conj search-query adding)
                       adding)]

      (assoc-in query [:bool query-type] new-filter))
    query))

(defn- add-filter
  [query field value]
  (add-to-query query :filter :term field value))

(defn- add-must-match
  [query field value]
  (add-to-query query :must :match field value))

(defn search
  [teams query-params]
  (let [conn (esr/connect c/elastic-search-endpoint)
        index (str c/elastic-search-index)
        params (keywordize-keys query-params)
        filtered (add-filter (filter-by-team teams) :org-uuid.keyword (:org params))
        query (add-must-match filtered :body (:q params))]
    (timbre/debug params)
    (timbre/debug filtered)
    (timbre/debug query)
    (doc/search-all-types conn index {:query query})))

;; Delete
(defn delete
  [uuid]
  (let [conn (esr/connect c/elastic-search-endpoint)
        index (str c/elastic-search-index)]
        doc/delete index "entry" uuid))