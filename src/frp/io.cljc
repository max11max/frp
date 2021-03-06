;primitives.event and io namespaces are separated to limit the impact of :refer-clojure :exclude for transduce
(ns frp.io
  (:require [aid.core :as aid :include-macros true]
            [cats.monad.maybe :as maybe]
            [com.rpl.specter :as s :include-macros true]
            [frp.derived :as derived]
            [frp.primitives.behavior :as behavior]
            [frp.primitives.event :as event]
            [frp.protocols :as protocols]
            [frp.tuple :as tuple])
  #?(:cljs (:require-macros [frp.io :refer [defcurriedmethod]])))

(defmulti get-effect (comp protocols/-get-keyword
                           second
                           vector))
;This definition of get-effect! produces the following failure in :advanced.
;Reloading Clojure file "/nodp/hfdp/observer/synchronization.clj" failed.
;clojure.lang.Compiler$CompilerException: java.lang.IllegalArgumentException: No method in multimethod 'get-effect!' for dispatch value
;(defmulti get-effect! (comp helpers/infer
;                            second
;                            vector))

(defmacro defcurriedmethod
  [multifn dispatch-val bindings & body]
  `(aid/defpfmethod ~multifn ~dispatch-val
                    (aid/curry ~(count bindings) (fn ~bindings
                                                   ~@body))))

(defcurriedmethod get-effect :event
                  [f! e network]
                  (run! (comp f!
                              tuple/snd)
                        (event/get-latests (:id e) network))
                  network)

(aid/defcurried get-network-value
  [b network]
  (behavior/get-value b (:time network) network))

(defn set-cache
  [b network]
  (s/setval [:cache (:id b)] (get-network-value b network) network))

(defcurriedmethod
  get-effect :behavior
  [f! b network]
  (->> network
       (set-cache b)
       (event/effect (aid/if-else (partial = network)
                                  (comp f!
                                        (get-network-value b))))))

(def on
  (comp (partial swap! event/network-state)
        ((aid/curry 3 s/setval*) [:effects s/AFTER-ELEM])
        get-effect))
