(ns frp.document
  (:require [cats.core :as m]
            [frp.browser :as browser :include-macros true]
            [frp.primitives.behavior :as behavior]))

(browser/defevent visibilitychange
  #(merge (browser/convert %)
          (browser/convert js/document)))

(browser/defbehavior hidden
  #(->> visibilitychange
        (m/<$> :hidden)
        (behavior/stepper js/document.hidden)))
