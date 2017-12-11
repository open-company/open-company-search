(ns dev
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :as ctnrepl]
            [oc.search.config :as c]
            [oc.search.components :as components]
            [oc.search.app :as app]))

(defonce system nil)

(defn init
  ([] (init c/search-server-port))
  ([port]
     (alter-var-root
      #'system
      (constantly (components/search-system
                   {:handler-fn app/app
                    :port port
                    :sqs-consumer
                    {:sqs-queue c/aws-sqs-search-index-queue
                     :message-handler app/sqs-handler
                     :sqs-creds {:access-key c/aws-access-key-id
                                 :secret-key c/aws-secret-access-key}}})))))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system (fn [s] (when s (component/stop s)))))

(defn go
  ([] (go c/search-server-port))
  ([port]
     (init port)
     (start)
     (app/echo-config)
     (println (str "Now serving search requests from the REPL.\n"
                   "When you're ready to stop the system, just type: (stop)\n"))
     port))

(defn reset []
  (stop)
  (ctnrepl/refresh :after 'user/go))
