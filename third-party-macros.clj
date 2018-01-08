;; This would be prefered to disabling suspicious-expression linter, it limits the scope to just slingshot,
;; but it's not working
; (disable-warning
;  {:linter :suspicious-expression
;   :for-macro 'clojure.core/and
;   :if-inside-macroexpansion-of #{'slingshot/try+}
;   :within-depth 20
;   :reason "slingshot/try+ creates calls to and with only 1 argument."})