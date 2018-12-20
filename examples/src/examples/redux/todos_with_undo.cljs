(ns examples.redux.todos-with-undo
  (:require [aid.core :as aid]
            [cats.core :as m]
            [com.rpl.specter :as s]
            [frp.clojure.core :as core]
            [frp.core :as frp]
            [frp.window :as window]))

(def typing
  (frp/event))

(def addition
  (frp/event))

(def deletion
  (frp/event))

(def undo
  (frp/event))

(def redo
  (frp/event))

(def todos
  ((aid/lift-a (fn [additions deletions]
                 (remove (comp deletions
                               last)
                         additions)))
    (->> frp/time
         (frp/snapshot (->> typing
                            (frp/stepper "")
                            ;TODO snapshot addition and time at the same time
                            (frp/snapshot addition)
                            (m/<$> second)
                            (core/remove empty?)))
         core/vector
         (frp/stepper []))
    (->> deletion
         core/vector
         (m/<$> set)
         (frp/stepper #{}))))

(defn todos-with-undo-component
  ;TODO implement this function
  [todos*]
  [:div
   [:form {:on-submit #(addition)}
    [:input {:on-change #(-> %
                             .-target.value
                             typing)}]
    [:button {:type "submit"}
     "Add Todo"]]
   (->> todos*
        (mapv (fn [[s t]]
                [:li {:on-click #(deletion t)}
                 s]))
        (s/setval s/BEFORE-ELEM :ul))
   [:div
    ;TODO extract a function that returns a button component
    [:button {:on-click #(undo)}
     "undo"]
    [:button {:on-click #(redo)}
     "redo"]]])

(def todos-with-undo
  (m/<$> todos-with-undo-component todos))

(frp/on (comp aid/funcall
              :prevent-default)
        window/submit)
