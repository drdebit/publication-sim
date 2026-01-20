(ns publication-sim.multidim
  (:gen-class)
  (:require [fastmath.random :as r]
            [fastmath.stats :as stats]
            [clojure.core.reducers :as reducers]
            [oz.core :as oz]))

;; Default configuration
(def default-config
  {:num-papers 1000
   :num-reviewers 2
   :dimensions [:interest :rigor]
   :noise-sd 30 ;; chosen from Bornnman paper on IRR
   :threshold 50})

;; Generate a paper with true quality on each dimension
(defn generate-paper [config]
  {:pid (str (java.util.UUID/randomUUID))
   :true-quality (into {} (map (fn [dim] 
                                  [dim (r/grand 50 20)])
                                (:dimensions config)))})

;; Generate all papers
(defn generate-papers [config]
  (vec (repeatedly (:num-papers config) #(generate-paper config))))

;; Single reviewer's noisy observation of a paper
(defn reviewer-observation [true-quality noise-sd]
  (into {} (map (fn [[dim true-val]]
                  [dim (+ true-val (r/grand 0 noise-sd))])
                true-quality)))

;; Aggregate multiple reviewers' observations (simple average)
(defn aggregate-reviews [paper config]
  (let [{:keys [num-reviewers noise-sd dimensions]} config
        observations (repeatedly num-reviewers 
                                #(reviewer-observation (:true-quality paper) noise-sd))
        avg-by-dim (into {} (map (fn [dim]
                                   [dim (/ (reduce + (map #(get % dim) observations))
                                          num-reviewers)])
                                 dimensions))]
    (assoc paper :observed-quality avg-by-dim)))

;; Decision: accept if ALL dimensions meet threshold
(defn accept-decision [paper threshold dimensions]
  (every? (fn [dim] (>= (get-in paper [:observed-quality dim]) threshold))
          dimensions))

;; Should paper be accepted based on TRUE quality?
(defn should-accept [paper threshold dimensions]
  (every? (fn [dim] (>= (get-in paper [:true-quality dim]) threshold))
          dimensions))

;; Run the review process
(defn simulate-review-process [config]
  (let [{:keys [threshold dimensions]} config
        papers (generate-papers config)]
    (map (fn [paper]
           (let [reviewed (aggregate-reviews paper config)
                 decision (accept-decision reviewed threshold dimensions)
                 correct (should-accept paper threshold dimensions)]
             (assoc reviewed
                    :decision decision
                    :should-accept correct
                    :outcome (cond
                              (and decision correct) :true-positive
                              (and decision (not correct)) :false-positive
                              (and (not decision) correct) :false-negative
                              :else :true-negative))))
         papers)))

;; Analysis functions

(defn outcome-counts [results]
  (frequencies (map :outcome results)))

(defn error-rates [results]
  (let [counts (outcome-counts results)
        tp (get counts :true-positive 0)
        fp (get counts :false-positive 0)
        tn (get counts :true-negative 0)
        fn (get counts :false-negative 0)
        total-positive (+ tp fn)  ; Should be accepted
        total-negative (+ tn fp)] ; Should be rejected
    {:false-negative-rate (if (pos? total-positive)
                            (/ (double fn) total-positive)
                            0)
     :false-positive-rate (if (pos? total-negative)
                            (/ (double fp) total-negative)
                            0)
     :accuracy (/ (double (+ tp tn)) (+ tp fp tn fn))
     :counts counts}))

(defn quality-by-decision [results]
  (let [accepted (filter :decision results)
        rejected (filter (complement :decision) results)
        mean-quality (fn [papers dim]
                      (if (seq papers)
                        (/ (reduce + (map #(get-in % [:true-quality dim]) papers))
                           (count papers))
                        0))]
    {:accepted (into {} (map (fn [dim]
                               [dim (mean-quality accepted dim)])
                             (:dimensions default-config)))
     :rejected (into {} (map (fn [dim]
                               [dim (mean-quality rejected dim)])
                             (:dimensions default-config)))
     :accepted-total (if (seq accepted)
                       (/ (reduce + (map (fn [p]
                                          (reduce + (vals (:true-quality p))))
                                        accepted))
                          (count accepted))
                       0)
     :rejected-total (if (seq rejected)
                       (/ (reduce + (map (fn [p]
                                          (reduce + (vals (:true-quality p))))
                                        rejected))
                          (count rejected))
                       0)}))

;; Calculate reliability (correlation between true and observed quality)
(defn calculate-reliability [results]
  (let [true-totals (map (fn [p] (reduce + (vals (:true-quality p)))) results)
        observed-totals (map (fn [p] (reduce + (vals (:observed-quality p)))) results)]
    (if (and (seq true-totals) (> (count true-totals) 1))
      (stats/correlation true-totals observed-totals)
      nil)))

;; Comprehensive analysis
(defn analyze-results [results]
  (merge
    (error-rates results)
    {:quality-stats (quality-by-decision results)
     :reliability (calculate-reliability results)}))

;; Run and analyze
(defn run-experiment [config]
  (let [results (simulate-review-process config)]
    (assoc (analyze-results results)
           :config config)))

;; Experiment 1: Vary number of reviewers (holding noise constant)
(defn reviewer-count-experiment []
  (let [reviewer-counts [1 2 3 4 5 7 10]]
    (mapv (fn [n]
            (let [config (assoc default-config :num-reviewers n)
                  results (run-experiment config)]
              {:num-reviewers n
               :false-negative-rate (:false-negative-rate results)
               :false-positive-rate (:false-positive-rate results)
               :accuracy (:accuracy results)
               :reliability (:reliability results)
               :quality-gap (- (get-in results [:quality-stats :accepted-total])
                              (get-in results [:quality-stats :rejected-total]))}))
          reviewer-counts)))

;; Experiment 2: Vary noise level (holding reviewers constant at 2)
(defn noise-level-experiment []
  (let [noise-levels [10 15 20 25 30]]
    (mapv (fn [noise]
            (let [config (assoc default-config :noise-sd noise)
                  results (run-experiment config)]
              {:noise-sd noise
               :false-negative-rate (:false-negative-rate results)
               :false-positive-rate (:false-positive-rate results)
               :accuracy (:accuracy results)
               :reliability (:reliability results)
               :quality-gap (- (get-in results [:quality-stats :accepted-total])
                              (get-in results [:quality-stats :rejected-total]))}))
          noise-levels)))

;; Experiment 3: Add third dimension
(defn dimension-experiment []
  (let [configs [{:dimensions [:interest :rigor]
                  :label "2-dimensions"}
                 {:dimensions [:interest :rigor :contribution]
                  :label "3-dimensions"}]]
    (mapv (fn [{:keys [dimensions label]}]
            (let [config (merge default-config {:dimensions dimensions})
                  results (run-experiment config)]
              {:label label
               :num-dimensions (count dimensions)
               :false-negative-rate (:false-negative-rate results)
               :false-positive-rate (:false-positive-rate results)
               :accuracy (:accuracy results)
               :reliability (:reliability results)}))
          configs)))

;; Experiment 4: Combined - vary reviewers AND dimensions
(defn combined-experiment []
  (let [reviewer-counts [1 2 3 5]
        dimension-sets [[:interest :rigor]
                       [:interest :rigor :contribution]]]
    (for [n reviewer-counts
          dims dimension-sets]
      (let [config (merge default-config 
                         {:num-reviewers n
                          :dimensions dims})
            results (run-experiment config)]
        {:num-reviewers n
         :num-dimensions (count dims)
         :false-negative-rate (:false-negative-rate results)
         :false-positive-rate (:false-positive-rate results)
         :accuracy (:accuracy results)}))))

;; Experiment 5: Vary selectivity/threshold
(defn selectivity-experiment []
  (let [thresholds [40 50 60 70]  ; From lenient to highly selective
        config (assoc default-config :noise-sd 30)]  ; Use empirically-grounded noise
    (mapv (fn [threshold]
            (let [updated-config (assoc config :threshold threshold)
                  results (run-experiment updated-config)]
              {:threshold threshold
               :false-negative-rate (:false-negative-rate results)
               :false-positive-rate (:false-positive-rate results)
               :accuracy (:accuracy results)
               :pct-accepted (/ (+ (get-in results [:counts :true-positive])
                                   (get-in results [:counts :false-positive]))
                               1000.0)}))
          thresholds)))

;; Run all experiments
(def exp1-results (reviewer-count-experiment))
(def exp2-results (noise-level-experiment))
(def exp3-results (dimension-experiment))
(def exp4-results (combined-experiment))
(def exp5-results (selectivity-experiment))

;; Pretty print results
(defn print-experiment [title results]
  (println "\n" title)
  (println "=====================================")
  (doseq [r results]
    (println r)))

(print-experiment "Experiment 1: Varying Reviewer Count" exp1-results)
(print-experiment "Experiment 2: Varying Noise Level" exp2-results)
(print-experiment "Experiment 3: Adding Dimensions" exp3-results)
(print-experiment "Experiment 4: Combined Effects" exp4-results)
(print-experiment "Experiment 5: Restricted Journal Set" exp5-results)

;; Experiment 6: Quality vs. Volume strategy analysis
;; Generate papers with FIXED quality levels to test acceptance probability

(defn generate-fixed-quality-papers
"Generate papers with a specific quality level on all dimensions"
  [quality-level num-papers dimensions]
  (vec (repeatedly num-papers
                   #(hash-map 
                     :pid (str (java.util.UUID/randomUUID))
                     :true-quality (into {} (map (fn [dim] [dim quality-level]) 
                                                 dimensions))))))

(defn acceptance-probability-for-quality
  "Run multiple trials to estimate acceptance probability for a given quality level"
  [quality-level config]
  (let [num-trials 1000
        test-config (assoc config :num-papers num-trials)
        ;; Generate papers with fixed quality
        papers (generate-fixed-quality-papers quality-level 
                                              num-trials 
                                              (:dimensions config))
        ;; Run through review process
        reviewed (map (fn [paper]
                       (let [reviewed (aggregate-reviews paper test-config)
                             decision (accept-decision reviewed 
                                                      (:threshold test-config) 
                                                      (:dimensions test-config))]
                         (assoc reviewed :decision decision)))
                     papers)
        ;; Calculate acceptance rate
        accepted-count (count (filter :decision reviewed))
        acceptance-rate (/ (double accepted-count) num-trials)]
    {:quality-level quality-level
     :acceptance-probability acceptance-rate
     :accepted-count accepted-count
     :total-trials num-trials}))

(defn quality-strategy-experiment
  "Compare acceptance probabilities across quality levels"
  []
  (let [quality-levels [60 65 70 75 80 85]
        config (merge default-config {:noise-sd 30
                                      :threshold 70
                                      :num-reviewers 2})]
    (mapv (fn [quality]
            (acceptance-probability-for-quality quality config))
          quality-levels)))

(defn expected-value-analysis
"Compare expected publications from different strategies"
  []
  (let [config (merge default-config {:noise-sd 30
                                      :threshold 70
                                      :num-reviewers 2})
        ;; Define strategies: {papers quality-per-paper}
        strategies {:one-excellent {:count 1 :quality 85}
                   :two-very-good {:count 2 :quality 78}
                   :two-good {:count 2 :quality 72}
                   :three-decent {:count 3 :quality 68}}]
    (into {}
          (map (fn [[strategy-name {:keys [count quality]}]]
                 (let [prob-result (acceptance-probability-for-quality quality config)
                       prob (:acceptance-probability prob-result)
                       expected-pubs (* count prob)]
                   [strategy-name {:papers count
                                  :quality-each quality
                                  :prob-accept-each prob
                                  :expected-publications expected-pubs
                                  :efficiency (/ expected-pubs count)}]))
               strategies))))

;; Run experiment 6
(def exp6-quality-probs (quality-strategy-experiment))
(def exp6-strategy-comparison (expected-value-analysis))

(defn print-quality-experiment []
  (println "\n Experiment 6a: Acceptance Probability by Quality Level")
  (println "=====================================")
  (doseq [result exp6-quality-probs]
    (println (format "Quality %d: %.3f acceptance probability" 
                     (:quality-level result)
                     (:acceptance-probability result))))
  
  (println "\n Experiment 6b: Strategy Comparison (Expected Publications)")
  (println "=====================================")
  (doseq [[strategy-name data] exp6-strategy-comparison]
    (println (format "%s: %d papers @ quality %d → %.3f expected pubs (%.1f%% efficiency)"
                     (name strategy-name)
                     (:papers data)
                     (:quality-each data)
                     (:expected-publications data)
                     (* 100 (:efficiency data))))))

(print-quality-experiment)
