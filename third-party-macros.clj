(disable-warning
 {:linter :suspicious-expression
  :for-macro 'clojure.core/and
  :if-inside-macroexpansion-of #{'slingshot.slingshot/try+}
  :within-depth 20
  :reason "slingshot.slingshot/try+ generates an (and ...) with one or more arguments. Suppress the 'and called with 1 args' warning."})