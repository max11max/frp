(ns frp.document
  (:require [cats.core :as m]
            [frp.browser :as browser :include-macros true]
            [frp.primitives.behavior :as behavior]))

(browser/defevent visibilitychange
  (fn [_]
    {:hidden js/document.hidden}))

(browser/defbehavior hidden
  #(->> visibilitychange
        (m/<$> :hidden)
        (behavior/stepper js/document.hidden)))
