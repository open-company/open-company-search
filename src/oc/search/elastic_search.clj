(ns oc.search.elastic-search
   (:require [taoensso.timbre :as timbre]
             [slingshot.slingshot :as slingshot]
             [clojure.walk :refer (keywordize-keys)]
             [clojurewerkz.elastisch.rest :as esr]
             [clojurewerkz.elastisch.rest.index :as idx]
             [clojurewerkz.elastisch.rest.document :as doc]
             [oc.search.config :as c]))


(defonce mapping-types
  {:doc {:properties {:type          {:type "text" :fields {:keyword {:type "keyword"}}}
                      :org-slug      {:type "text" :store true :index false}
                      :org-name      {:type "text" :store true :index false}
                      :org-uuid      {:type "text" :fields {:keyword {:type "keyword"}}}
                      :org-team-uuid {:type "text" :fields {:keyword {:type "keyword"}}}
                      :board-uuid    {:type "text" :fields {:keyword {:type "keyword"}}}
                      :board-name    {:type "text"}
                      :board-slug    {:type "text"}
                      :access        {:type "text" :fields {:keyword {:type "keyword"}}}
                      :viewer-id     {:type "text" :fields {:keyword {:type "keyword"}}}
                      :headline      {:type "text" :analyzer "snowball"}
                      :author-url    {:type "text" }
                      :author-name   {:type "text" }
                      :author-id     {:type "text" :fields {:keyword {:type "keyword"}}}
                      :secure-uuid   {:type "text" :fields {:keyword {:type "keyword"}}}
                      :uuid          {:type "text" :fields {:keyword {:type "keyword"}}}
                      :status        {:type "text" :fields {:keyword {:type "keyword"}}}
                      :name          {:type "text"}
                      :slug          {:type "text"}
                      :updated-at    {:type "date"}
                      :published-at  {:type "date"}
                      :shared-at     {:type "date"}
                      :created-at    {:type "date"}
                      :body          {:type "text" :analyzer "snowball"
                                      :term_vector "with_positions_offsets"}}}})

(defn- create-index
  [conn index-name opts]
  (let [{:keys [settings mappings]} opts]
    (esr/put conn (esr/index-url conn index-name)
             {:body (if mappings
                      {:settings settings :mappings mappings}
                      {:settings settings})})))

(defn start []
  (let [conn (esr/connect c/elastic-search-endpoint {:content-type :json})
        index (str c/elastic-search-index)]
    (timbre/debug "connected...does index exist?" index)
    ;; query for index, if not there create
    (let [exists? (idx/exists? conn index)
          request-exists? (esr/head conn (esr/index-url conn index))
          mapping (idx/get-mapping conn index)]
      (timbre/debug exists? request-exists? mapping)
      (when-not exists?
        (timbre/info "index does not exist...creating.")
        (slingshot/try+
         (timbre/info (create-index conn index {:mappings mapping-types :settings {}}))
         (catch [:status 400] {:keys [request-time headers body]}
           (timbre/error request-time headers body)))))
    conn))

(defn stop [])

;; ----- Indexing data -----

(defn- multi-value
  "Create multi-value fields"
  [attr values]
  (vec (distinct (map attr values))))

(defn- map-entry
  [data]
  (let [entry (:new (:content data))
        org (:org data)
        board (:board data)]
    (timbre/debug org)
    (timbre/debug board)
    (timbre/debug entry)
    {:type "entry"
     :org-slug (:slug org)
     :org-name (:name org)
     :org-uuid (:uuid org)
     :org-team-id (:team-id org)
     :board-uuid (:uuid board)
     :board-name (:name board)
     :board-slug (:slug board)
     :access (:access board)
     :viewer-id (multi-value :user-id (:viewers entry))
     :author-id (multi-value :user-id (:author entry))
     :author-name (multi-value :name (:author entry))
     :author-url (multi-value :avatar-url (:author entry))
     :headline (:headline entry)
     :secure-uuid (:secure-uuid entry)
     :uuid (str "entry-" (:uuid entry))
     :status (:status entry)
     :updated-at (:updated-at entry)
     :published-at (:published-at entry)
     :shared-at (:shared-at (last (:shared entry)))
     :created-at (:created-at entry)
     :body (:body entry)}))

(defn- map-board
  [data]
  (let [org (:org data)
        board (:new (:content data))]
    (timbre/debug org)
    (timbre/debug board)
    {:type "board"
     :org-slug (:slug org)
     :org-name (:name org)
     :org-uuid (:uuid org)
     :org-team-id (:team-id org)
     :uuid (str "board-" (:uuid board))
     :updated-at (:updated-at board)
     :created-at (:created-at board)
     :slug (:slug board)
     :name (:name board)
     :author-id (:user-id (:author board))
     :author-name (:name (:author board))
     :author-url (:avatar-url (:author board))
     }))

(defn- map-data
  [data]
  (cond
   (= "entry" (:resource-type data)) (map-entry data)
   (= "board" (:resource-type data)) (map-board data)))

;; ----- Upsert -----

(defn- add-index
  [data-type data]
  (let [conn (esr/connect c/elastic-search-endpoint {:content-type :json})
        index (str c/elastic-search-index)
        id (str data-type "-" (:uuid (:new (:content data))))]
    (timbre/info data)
    (timbre/info (map-data data))
    (timbre/info
     (doc/upsert conn index "doc" id (map-data data)))))

(defn add-entry-index
  [entry-data]
  (add-index "entry" entry-data))

(defn add-board-index
  [board-data]
  (add-index "board" board-data))

;; ----- Search -----

(defn- filter-by-team
  [teams]
  {:bool
   {:filter
    {:bool
     {:must [{:bool
              {:should (vec (map (fn [team] {:term
                                             {:org-team-id.keyword team}})
                                 teams))}}]}}}})

(defn- filter-private [query uuid]
  (let [query-type [:bool :filter :bool :must]
        filter-query (get-in query query-type)
        adding {:bool
                {:should [{:bool
                           {:must_not {:term {:access "private"}}}
                           },
                          {:term {:author-id.keyword uuid}}
                          {:term {:viewer-id.keyword uuid}}]}}]
    (assoc-in query query-type (vec (cons adding filter-query)))))

(defn- filter-drafts [query uuid]
  (let [query-type [:bool :filter :bool :must]
        filter-query (get-in query query-type)
        adding {:bool
                {:should [{:bool
                           {:must {:term {:status "published"}}}
                           },
                          {:term {:author-id.keyword uuid}}]}}]
    (assoc-in query query-type (vec (cons adding filter-query)))))

(defn- add-to-query
  [query query-type search-term field value]
  (if value
    (let [search-query (get-in query query-type)
          adding {search-term {field value}}
          new-search (vec (cons adding search-query))]
      (assoc-in query query-type new-search))
    query))

(defn- add-should-match
  [query field value]
  (add-to-query query [:bool :should] :match field value))

(defn search
  [query-params]
  (let [conn (esr/connect c/elastic-search-endpoint {:content-type :json})
        index (str c/elastic-search-index)
        params (keywordize-keys query-params)
        teams (:teams params)
        query (-> (filter-by-team teams)
                  (filter-private (:uuid params))
                  (filter-drafts (:uuid params))
                  (add-to-query [:bool :filter :bool :must]
                                :term :type.keyword "entry")
                  (add-to-query [:bool :filter :bool :must]
                                :term :org-uuid.keyword (:org params))
                  (add-should-match :body (:q params))
                  (add-should-match :headline (:q params))
                  (add-should-match :author-name (:q params))
                  (add-should-match :name (:q params))
                  (add-should-match :slug (:q params))
                  )]
    (timbre/info query)
    (doc/search-all-types conn index {:query query
                                      :min_score "0.001"
                                      })))

;; ----- Delete -----

(defn- delete
  [data-type data]
  (let [uuid (:uuid (:old (:content data)))
        conn (esr/connect c/elastic-search-endpoint {:content-type :json})
        index (str c/elastic-search-index)]
    (timbre/info (doc/delete conn index "doc" (str data-type "-" uuid)))))

(defn delete-entry [data] (delete "entry" data))

(defn delete-board [data] (delete "board" data))