(ns dev
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :as ctnrepl]
            [oc.search.config :as c]
            [oc.search.components :as components]
            [oc.search.app :as app]))

(def system nil)

(defn init [] (alter-var-root
               #'system
               (constantly (components/search-system
                            {:sqs-consumer
                             {:sqs-queue c/aws-sqs-search-index-queue
                              :message-handler app/sqs-handler
                              :sqs-creds {:access-key c/aws-access-key-id
                                          :secret-key c/aws-secret-access-key}}}))))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system (fn [s] (when s (component/stop s)))))

(defn go []
  (init)
  (start)
  (app/echo-config)
  (println (str "Now serving search requests from the REPL.\n"
                "When you're ready to stop the system, just type: (stop)\n"))
  :ok)

(defn reset []
  (stop)
  (ctnrepl/refresh :after 'user/go))