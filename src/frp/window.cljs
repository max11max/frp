(ns frp.window
  (:require [cats.core :as m]
            [com.rpl.specter :as s]
            [frp.primitives.behavior :as behavior :include-macros true]
            [frp.primitives.event :as event]
            [frp.browser :as browser]))

(def mousemove
  (event/->Event ::mousemove))

(def mouseup
  (event/->Event ::mouseup))

(def popstate
  (event/->Event ::popstate))

(def resize
  (event/->Event ::resize))

(def inner-height
  (behavior/->Behavior ::inner-height))

(defn add-remove-listener
  [event-type listener]
  (js/addEventListener event-type listener)
  (swap! event/network-state
         (partial s/setval*
                  :cancel
                  (fn [_]
                    (js/removeEventListener event-type listener)))))

(defn redef-listen
  [e f]
  (browser/redef e)
  (-> e
      :id
      name
      (add-remove-listener f)))

(behavior/register
  (behavior/redef inner-height
                  (->> resize
                       (m/<$> :inner-height)
                       (behavior/stepper js/innerHeight)))

  (redef-listen mousemove
                ;(.-movementX %) is undefined in :advanced.
                #(mousemove {:movement-x (aget % "movementX")
                             :movement-y (aget % "movementY")}))

  (redef-listen mouseup
                (fn [_]
                  (mouseup {})))

  (redef-listen popstate
                (fn [_]
                  (popstate {:location {:pathname js/location.pathname}})))

  (redef-listen resize
                (fn [_]
                  (resize {:inner-height js/innerHeight}))))
