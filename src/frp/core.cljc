(ns frp.core
  (:refer-clojure :exclude [stepper time transduce])
  (:require [frp.derived :as derived]
            [frp.io :as io]
            [frp.primitives.behavior :as behavior]
            [frp.primitives.event :as event]
    ;TODO don't require browser namespaces
            #?@(:cljs [[frp.document]
                       [frp.location]]))
  #?(:cljs (:require-macros frp.core)))

(def restart
  behavior/restart)

(def event
  derived/event)

(defmacro defe
  [& names]
  `(derived/defe ~@names))

(def behavior
  derived/behavior)

(def time
  behavior/time)

(def stepper
  behavior/stepper)

(def transduce
  event/transduce)

(def snapshot
  event/snapshot)

(defmacro activate
  ([]
   `(event/activate))
  ([rate]
   `(event/activate ~rate)))

(def on
  io/on)

(defmacro transparent
  [expr]
  `(derived/transparent ~expr))

(defmacro with-undo
  [& more]
  `(derived/with-undo ~@more))

(def accum
  derived/accum)

(def switcher
  derived/switcher)

;TODO move this expression to behavior
(restart)
