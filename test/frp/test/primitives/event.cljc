(ns frp.test.primitives.event
  (:refer-clojure :exclude [transduce])
  (:require [clojure.walk :as walk]
            [aid.core :as aid]
            [aid.unit :as unit]
            [#?(:clj  clojure.test
                :cljs cljs.test) :as test :include-macros true]
            [cats.context :as ctx]
            [cats.core :as m]
            [cats.monad.maybe :as maybe]
            [com.rpl.specter :as s]
            [clojure.test.check]
            [clojure.test.check.clojure-test
             :as clojure-test
             :include-macros true]
            [clojure.test.check.generators :as gen]
            [frp.core :as frp]
            [frp.helpers :as helpers]
            [frp.primitives.event :as event]
            [frp.time :as time]
            [frp.tuple :as tuple]
            [frp.test.helpers :as test-helpers :include-macros true]))

(clojure-test/defspec call-inactive
  test-helpers/cljc-num-tests
  (test-helpers/set-up-for-all [as (gen/vector test-helpers/any-equal)]
                               (let [e (frp/event)]
                                 (run! e as)
                                 (empty? @e))))

(def recursively-get-occs
  #(walk/postwalk (aid/if-then event/event?
                               (comp recursively-get-occs
                                     deref))
                  %))

(def equal
  (comp (partial apply =)
        recursively-get-occs
        vector))

(def last-equal
  (comp (aid/build or
                   (partial every? empty?)
                   (comp (partial apply equal)
                         (partial map last)))
        vector))

(clojure-test/defspec call-active
  test-helpers/cljc-num-tests
  (test-helpers/set-up-for-all [as (gen/vector test-helpers/any-equal)]
                               (let [e (frp/event)]
                                 (frp/activate)
                                 (run! e as)
                                 (last-equal (map tuple/snd @e) as))))

(clojure-test/defspec <$>-identity
  test-helpers/cljc-num-tests
  (test-helpers/set-up-for-all
    [input-event test-helpers/any-event
     ;TODO consider cases where f has side effects
     f (gen/one-of [(gen/return frp/event)
                    (test-helpers/function test-helpers/any-equal)])
     as (gen/vector test-helpers/any-equal)]
    (let [occs @input-event
          fmapped-event (m/<$> f input-event)]
      (frp/activate)
      (run! input-event as)
      (last-equal (map tuple/snd @fmapped-event)
                  (map f (concat (map tuple/snd occs) as))))))

(clojure-test/defspec pure-identity
  test-helpers/cljc-num-tests
  (test-helpers/set-up-for-all [a test-helpers/any-equal]
                               (= (last @(-> (frp/event)
                                             ctx/infer
                                             (m/pure a)))
                                  (tuple/tuple time/epoch a))))

(def join-generator
  ;TODO refactor
  ;TODO generate an event with pure
  (gen/let [outer-event test-helpers/mempty-event
            probabilities* (test-helpers/probabilities 0)
            inner-events (-> probabilities*
                             test-helpers/get-events
                             gen/return)
            as (->> inner-events
                    count
                    (gen/vector test-helpers/any-equal))
            ;TODO interleave outer-calls and inner-calls without violating the constraint of E_{E_a}
            outer-calls (->> inner-events
                             (map #(partial outer-event %))
                             gen/shuffle)
            ;TODO call inner-event multiple times
            inner-calls (gen/shuffle (map partial inner-events as))
            calls (gen/return (concat outer-calls inner-calls))]
    (gen/tuple (gen/return outer-event)
               (gen/return inner-events)
               (gen/return calls))))

(clojure-test/defspec join-identity
  test-helpers/cljc-num-tests
  (test-helpers/set-up-for-all
    [[outer-event inner-events calls] join-generator]
    (let [joined-event (m/join outer-event)]
      (frp/activate)
      (test-helpers/run-calls! calls)
      (last-equal (->> inner-events
                       (map deref)
                       (reduce event/merge-occs []))
                  @joined-event))))

(def <>-generator
  ;TODO refactor
  (gen/let [probabilities (gen/vector test-helpers/probability 2)
            input-events (-> probabilities
                             test-helpers/get-events
                             gen/return)
            ns (->> input-events
                    count
                    (gen/vector (gen/sized (partial gen/choose 0))))
            calls (gen/shuffle (mapcat (fn [n e]
                                         (repeat n (partial e unit/unit)))
                                       ns
                                       input-events))]
    (gen/tuple (gen/return input-events)
               (gen/return (apply m/<> input-events))
               (gen/return calls))))

(clojure-test/defspec <>-identity
  test-helpers/cljc-num-tests
  (test-helpers/set-up-for-all [[input-events mappended-event calls]
                                <>-generator]
                               (frp/activate)
                               (test-helpers/run-calls! calls)
                               (->> input-events
                                    (map deref)
                                    (apply event/merge-occs)
                                    (last-equal @mappended-event))))

(test/deftest event-mempty
  (-> @(-> (frp/event)
           ctx/infer
           m/mempty)
      (= [])
      test/is))

(defn get-generators
  [generator xforms**]
  (map (partial (aid/flip gen/fmap) generator) xforms**))

(def any-nilable-equal
  (gen/one-of [test-helpers/any-equal (gen/return nil)]))

(def xform*
  (gen/one-of
    (concat (map (comp gen/return
                       aid/funcall)
                 [dedupe distinct])
            (get-generators gen/s-pos-int [take-nth partition-all])
            (get-generators gen/int [drop take])
            (get-generators (test-helpers/function gen/boolean)
                            [drop-while filter remove take-while])
            (get-generators (test-helpers/function test-helpers/any-equal)
                            [map map-indexed partition-by])
            (get-generators (test-helpers/function any-nilable-equal)
                            [keep keep-indexed])
            [(gen/fmap replace (gen/map test-helpers/any-equal
                                        test-helpers/any-equal))
             (gen/fmap interpose test-helpers/any-equal)]
            ;Composing mapcat more than once seems to make the test to run longer than 10 seconds.
            ;[(gen/fmap mapcat (test-helpers/function (gen/vector test-helpers/any-equal)))]
            )))

(def xform
  (->> xform*
       gen/vector
       gen/not-empty
       (gen/fmap (partial apply comp))))

(defn get-elements
  [xf earliests as]
  (maybe/map-maybe (partial (comp unreduced
                                  (xf (comp maybe/just
                                            second
                                            vector)))
                            aid/nothing)
                   (concat earliests as)))

(clojure-test/defspec transduce-identity
  test-helpers/cljc-num-tests
  ;TODO refactor
  (test-helpers/set-up-for-all
    [input-event test-helpers/any-event
     xf xform
     f (gen/one-of [(test-helpers/function test-helpers/any-equal)
                    (gen/return (comp frp/event
                                      vector))])
     init test-helpers/any-equal
     as (gen/vector test-helpers/any-equal)]
    (let [transduced-event (frp/transduce xf f init input-event)
          earliests @input-event]
      (frp/activate)
      (run! input-event as)
      (->> as
           (get-elements xf (map tuple/snd earliests))
           (reductions f init)
           rest
           (last-equal (map tuple/snd @transduced-event))))))

(clojure-test/defspec cat-identity
  test-helpers/cljc-num-tests
  ;TODO refactor
  (test-helpers/set-up-for-all
    ;TODO generate an event with pure
    [input-event test-helpers/mempty-event
     f! (gen/one-of [(test-helpers/function test-helpers/any-equal)
                     (gen/return (comp frp/event
                                       vector))])
     init test-helpers/any-equal
     ;TODO generate list
     as (gen/vector (gen/vector test-helpers/any-equal))]
    ;TODO compose xforms
    (let [cat-event (frp/transduce cat f! init input-event)
          map-event (frp/transduce (comp (remove empty?)
                                         (map last))
                                   f!
                                   init
                                   input-event)]
      (frp/activate)
      (run! input-event as)
      (last-equal @cat-event @map-event))))

(clojure-test/defspec snapshot-identity
  test-helpers/cljc-num-tests
  (test-helpers/set-up-for-all
    ;TODO generate a behavior by stepper and call the event
    [input-behavior test-helpers/any-behavior
     input-event test-helpers/any-event
     as (gen/vector test-helpers/any-equal)]
    (let [snapshotted-event (frp/snapshot input-event input-behavior)]
      (frp/activate)
      (run! input-event as)
      (= (map (comp first
                    tuple/snd)
              @snapshotted-event)
         (map tuple/snd
              @input-event)))))
