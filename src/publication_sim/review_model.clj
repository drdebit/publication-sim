(ns publication-sim.review-model
  "Core peer review simulation model.

   Separates two distinct roles:
   - Reviewers: Provide noisy quality assessments that are averaged
   - Editor: Makes accept/reject decision based on aggregated reviewer signal

   The supplemental 'editor-as-reviewer' mode allows the editor to also
   contribute a (noisier) signal to the quality average."
  (:require [fastmath.random :as r]
            [fastmath.stats :as stats]))

;; =============================================================================
;; Configuration
;; =============================================================================

(def default-config
  {:num-papers 1000
   :num-reviewers 2
   :dimensions [:interest :rigor]
   :reviewer-noise-sd 30      ; Calibrated to Bornmann IRR findings
   :threshold 50
   :quality-mean 50
   :quality-sd 20
   ;; Supplemental analysis options
   :editor-as-reviewer? false
   :editor-noise-sd 50})      ; Editor's shallower dive = noisier signal

;; =============================================================================
;; Paper Generation
;; =============================================================================

(defn generate-paper
  "Generate a paper with true quality drawn from N(mean, sd) on each dimension."
  [{:keys [dimensions quality-mean quality-sd]}]
  {:pid (str (java.util.UUID/randomUUID))
   :true-quality (into {}
                       (map (fn [dim] [dim (r/grand quality-mean quality-sd)])
                            dimensions))})

(defn generate-papers
  "Generate n papers according to config."
  [{:keys [num-papers] :as config}]
  (vec (repeatedly num-papers #(generate-paper config))))

;; =============================================================================
;; Reviewer Assessment
;; =============================================================================

(defn reviewer-observation
  "Single reviewer's noisy observation of paper's true quality.
   Returns a map of dimension -> observed value."
  [true-quality noise-sd]
  (into {}
        (map (fn [[dim true-val]]
               [dim (+ true-val (r/grand 0 noise-sd))])
             true-quality)))

(defn aggregate-reviewer-observations
  "Average multiple reviewers' independent noisy observations.
   Returns the mean observed quality on each dimension."
  [true-quality num-reviewers noise-sd]
  (let [observations (repeatedly num-reviewers
                                 #(reviewer-observation true-quality noise-sd))
        dimensions (keys true-quality)]
    (into {}
          (map (fn [dim]
                 [dim (/ (reduce + (map #(get % dim) observations))
                         num-reviewers)])
               dimensions))))

(defn aggregate-with-editor
  "Aggregate reviewer observations AND editor's observation.
   Used for supplemental 'editor-as-reviewer' analysis.

   Editor's observation is weighted equally with each reviewer,
   but has higher noise (shallower evaluation)."
  [true-quality num-reviewers reviewer-noise-sd editor-noise-sd]
  (let [reviewer-obs (repeatedly num-reviewers
                                 #(reviewer-observation true-quality reviewer-noise-sd))
        editor-obs (reviewer-observation true-quality editor-noise-sd)
        all-obs (conj (vec reviewer-obs) editor-obs)
        dimensions (keys true-quality)
        total-observers (inc num-reviewers)]
    (into {}
          (map (fn [dim]
                 [dim (/ (reduce + (map #(get % dim) all-obs))
                         total-observers)])
               dimensions))))

;; =============================================================================
;; Editor Decision
;; =============================================================================

(defn editor-decision
  "Editor accepts paper if ALL dimensions meet threshold.
   This AND-gating reflects typical journal requirements."
  [observed-quality threshold dimensions]
  (every? (fn [dim] (>= (get observed-quality dim) threshold))
          dimensions))

(defn true-decision
  "What the decision SHOULD be based on true quality."
  [true-quality threshold dimensions]
  (every? (fn [dim] (>= (get true-quality dim) threshold))
          dimensions))

;; =============================================================================
;; Review Process Simulation
;; =============================================================================

(defn review-paper
  "Simulate the full review process for a single paper.

   1. Reviewers observe paper quality with noise
   2. Observations are aggregated (averaged)
   3. Editor makes accept/reject decision based on aggregate

   If :editor-as-reviewer? is true, editor also contributes to aggregate."
  [paper {:keys [num-reviewers reviewer-noise-sd threshold dimensions
                 editor-as-reviewer? editor-noise-sd] :as config}]
  (let [true-quality (:true-quality paper)

        ;; Aggregate quality assessment
        observed-quality (if editor-as-reviewer?
                           (aggregate-with-editor true-quality
                                                  num-reviewers
                                                  reviewer-noise-sd
                                                  editor-noise-sd)
                           (aggregate-reviewer-observations true-quality
                                                            num-reviewers
                                                            reviewer-noise-sd))

        ;; Editor's decision
        decision (editor-decision observed-quality threshold dimensions)
        should-accept (true-decision true-quality threshold dimensions)

        ;; Classification outcome
        outcome (cond
                  (and decision should-accept)       :true-positive
                  (and decision (not should-accept)) :false-positive
                  (and (not decision) should-accept) :false-negative
                  :else                              :true-negative)]

    (assoc paper
           :observed-quality observed-quality
           :decision decision
           :should-accept should-accept
           :outcome outcome)))

(defn simulate-review-process
  "Run the review process on all papers."
  [config]
  (let [papers (generate-papers config)]
    (map #(review-paper % config) papers)))

;; =============================================================================
;; Analysis Functions
;; =============================================================================

(defn outcome-counts
  "Count papers by outcome type."
  [results]
  (frequencies (map :outcome results)))

(defn error-rates
  "Calculate false negative rate, false positive rate, and accuracy."
  [results]
  (let [counts (outcome-counts results)
        tp (get counts :true-positive 0)
        fp (get counts :false-positive 0)
        tn (get counts :true-negative 0)
        fn (get counts :false-negative 0)
        total-should-accept (+ tp fn)
        total-should-reject (+ tn fp)
        total (+ tp fp tn fn)]
    {:true-positives tp
     :false-positives fp
     :true-negatives tn
     :false-negatives fn
     :false-negative-rate (if (pos? total-should-accept)
                            (/ (double fn) total-should-accept)
                            0.0)
     :false-positive-rate (if (pos? total-should-reject)
                            (/ (double fp) total-should-reject)
                            0.0)
     :accuracy (if (pos? total)
                 (/ (double (+ tp tn)) total)
                 0.0)
     :acceptance-rate (if (pos? total)
                        (/ (double (+ tp fp)) total)
                        0.0)}))

(defn quality-by-decision
  "Calculate mean true quality for accepted vs rejected papers."
  [results dimensions]
  (let [accepted (filter :decision results)
        rejected (remove :decision results)
        mean-total-quality (fn [papers]
                             (if (seq papers)
                               (/ (reduce + (map (fn [p]
                                                   (reduce + (vals (:true-quality p))))
                                                 papers))
                                  (count papers))
                               0.0))]
    {:accepted-mean-quality (mean-total-quality accepted)
     :rejected-mean-quality (mean-total-quality rejected)
     :quality-gap (- (mean-total-quality accepted)
                     (mean-total-quality rejected))}))

(defn reliability
  "Calculate correlation between true and observed total quality."
  [results]
  (let [true-totals (map #(reduce + (vals (:true-quality %))) results)
        observed-totals (map #(reduce + (vals (:observed-quality %))) results)]
    (if (and (> (count true-totals) 1)
             (not= (apply min true-totals) (apply max true-totals)))
      (stats/correlation true-totals observed-totals)
      nil)))

(defn analyze
  "Run full analysis on simulation results."
  [results config]
  (merge
   (error-rates results)
   (quality-by-decision results (:dimensions config))
   {:reliability (reliability results)
    :config (select-keys config [:num-reviewers :reviewer-noise-sd
                                 :threshold :dimensions
                                 :editor-as-reviewer? :editor-noise-sd])}))

;; =============================================================================
;; Experiment Runners
;; =============================================================================

(defn run-experiment
  "Run simulation with given config and return analysis."
  [config]
  (let [merged-config (merge default-config config)
        results (simulate-review-process merged-config)]
    (analyze results merged-config)))

(defn run-experiment-n
  "Run experiment n times and average results for stability."
  [config n]
  (let [runs (repeatedly n #(run-experiment config))
        avg (fn [k] (/ (reduce + (map k runs)) n))]
    {:num-reviewers (:num-reviewers (merge default-config config))
     :false-negative-rate (avg :false-negative-rate)
     :false-positive-rate (avg :false-positive-rate)
     :accuracy (avg :accuracy)
     :reliability (avg :reliability)
     :quality-gap (avg :quality-gap)
     :config (:config (first runs))}))

;; =============================================================================
;; Main Experiments for Paper
;; =============================================================================

(defn experiment-reviewer-count
  "Experiment 1: Effect of reviewer count on decision quality."
  [& {:keys [reviewer-counts num-runs]
      :or {reviewer-counts [1 2 3 4 5 7 10]
           num-runs 10}}]
  (mapv (fn [n]
          (run-experiment-n {:num-reviewers n} num-runs))
        reviewer-counts))

(defn experiment-noise-level
  "Experiment 2: Effect of reviewer noise on decision quality."
  [& {:keys [noise-levels num-runs]
      :or {noise-levels [10 15 20 25 30 35 40]
           num-runs 10}}]
  (mapv (fn [noise]
          (assoc (run-experiment-n {:reviewer-noise-sd noise} num-runs)
                 :noise-sd noise))
        noise-levels))

(defn experiment-dimensions
  "Experiment 3: Effect of evaluation dimensions (AND-gating)."
  [& {:keys [num-runs]
      :or {num-runs 10}}]
  (let [dim-configs [{:dimensions [:interest :rigor]
                      :label "2 dimensions"}
                     {:dimensions [:interest :rigor :contribution]
                      :label "3 dimensions"}
                     {:dimensions [:interest :rigor :contribution :novelty]
                      :label "4 dimensions"}]]
    (mapv (fn [{:keys [dimensions label]}]
            (assoc (run-experiment-n {:dimensions dimensions} num-runs)
                   :label label
                   :num-dimensions (count dimensions)))
          dim-configs)))

(defn experiment-threshold
  "Experiment 4: Effect of selectivity (threshold) on error rates."
  [& {:keys [thresholds num-runs]
      :or {thresholds [40 50 60 70]
           num-runs 10}}]
  (mapv (fn [thresh]
          (assoc (run-experiment-n {:threshold thresh} num-runs)
                 :threshold thresh))
        thresholds))

(defn experiment-quality-strategy
  "Experiment 5: Quality vs volume tradeoff.
   For papers of a given quality level, estimate acceptance probability."
  [& {:keys [quality-levels num-trials]
      :or {quality-levels [60 65 70 75 80 85]
           num-trials 1000}}]
  (let [config (merge default-config {:threshold 70})]
    (mapv (fn [quality]
            (let [;; Generate papers with fixed quality on all dimensions
                  papers (vec (repeatedly num-trials
                                          #(hash-map
                                            :pid (str (java.util.UUID/randomUUID))
                                            :true-quality (into {}
                                                                (map (fn [d] [d quality])
                                                                     (:dimensions config))))))
                  results (map #(review-paper % config) papers)
                  accepted (count (filter :decision results))]
              {:quality-level quality
               :acceptance-probability (/ (double accepted) num-trials)}))
          quality-levels)))

(defn experiment-strategy-comparison
  "Compare expected publications under different quality/volume strategies."
  [& {:keys [strategies]
      :or {strategies {:one-excellent    {:papers 1 :quality 85}
                       :two-very-good    {:papers 2 :quality 78}
                       :two-good         {:papers 2 :quality 72}
                       :three-decent     {:papers 3 :quality 68}}}}]
  (let [quality-probs (into {}
                            (map (fn [{:keys [quality-level acceptance-probability]}]
                                   [quality-level acceptance-probability])
                                 (experiment-quality-strategy)))]
    (into {}
          (map (fn [[strategy-name {:keys [papers quality]}]]
                 (let [prob (or (get quality-probs quality)
                                (:acceptance-probability
                                 (first (experiment-quality-strategy
                                         :quality-levels [quality]))))
                       expected (* papers prob)]
                   [strategy-name
                    {:papers papers
                     :quality quality
                     :prob-accept prob
                     :expected-publications expected}]))
               strategies))))

;; =============================================================================
;; Supplemental Analysis: Editor as Partial Reviewer
;; =============================================================================

(defn experiment-editor-as-reviewer
  "Supplemental: Compare main model vs editor-as-reviewer model.
   Shows that adding editor's noisy signal to average reduces accuracy."
  [& {:keys [reviewer-counts editor-noise-levels num-runs]
      :or {reviewer-counts [1 2 3]
           editor-noise-levels [50 60]
           num-runs 10}}]
  (let [;; Main model results (editor does NOT contribute to average)
        main-results (for [n reviewer-counts]
                       (assoc (run-experiment-n {:num-reviewers n
                                                 :editor-as-reviewer? false}
                                                num-runs)
                              :model "main"
                              :editor-noise-sd nil))

        ;; Editor-as-reviewer results
        supplemental-results (for [n reviewer-counts
                                   editor-noise editor-noise-levels]
                               (assoc (run-experiment-n {:num-reviewers n
                                                         :editor-as-reviewer? true
                                                         :editor-noise-sd editor-noise}
                                                        num-runs)
                                      :model "editor-as-reviewer"
                                      :editor-noise-sd editor-noise))]

    (concat main-results supplemental-results)))

;; =============================================================================
;; Formatted Output
;; =============================================================================

(defn format-results-table
  "Format experiment results as a printable table."
  [results columns]
  (let [header (clojure.string/join " | " (map name columns))
        separator (clojure.string/join "-+-" (map #(apply str (repeat (count (name %)) "-")) columns))
        rows (map (fn [r]
                    (clojure.string/join " | "
                                         (map (fn [c]
                                                (let [v (get r c)]
                                                  (cond
                                                    (float? v) (format "%.3f" v)
                                                    (double? v) (format "%.3f" v)
                                                    (ratio? v) (format "%.3f" (double v))
                                                    :else (str v))))
                                              columns)))
                  results)]
    (clojure.string/join "\n" (concat [header separator] rows))))

(defn print-experiment
  "Print experiment results with title."
  [title results columns]
  (println)
  (println title)
  (println (apply str (repeat (count title) "=")))
  (println (format-results-table results columns)))

;; =============================================================================
;; Interaction Experiments
;; =============================================================================

(defn experiment-reviewers-x-noise
  "Interaction: How does the benefit of additional reviewers vary with noise level?

   Hypothesis: More reviewers help MORE when base noise is higher, because
   averaging reduces variance proportionally (by 1/sqrt(n))."
  [& {:keys [reviewer-counts noise-levels num-runs]
      :or {reviewer-counts [1 2 3 5]
           noise-levels [15 25 35]
           num-runs 10}}]
  (vec
   (for [noise noise-levels
         n reviewer-counts]
     (assoc (run-experiment-n {:num-reviewers n
                               :reviewer-noise-sd noise}
                              num-runs)
            :noise-sd noise))))

(defn experiment-quality-x-noise
  "Interaction: How does acceptance probability vary with quality at different noise levels?

   Shows that borderline papers (quality near threshold) are most affected by noise.
   Very high or very low quality papers get correct decisions regardless of noise."
  [& {:keys [quality-levels noise-levels num-trials]
      :or {quality-levels [50 60 70 80 90]
           noise-levels [15 30 45]
           num-trials 1000}}]
  (vec
   (for [noise noise-levels
         quality quality-levels]
     (let [config (merge default-config {:reviewer-noise-sd noise
                                         :threshold 70})
           papers (vec (repeatedly num-trials
                                   #(hash-map
                                     :pid (str (java.util.UUID/randomUUID))
                                     :true-quality (into {}
                                                         (map (fn [d] [d quality])
                                                              (:dimensions config))))))
           results (map #(review-paper % config) papers)
           accepted (count (filter :decision results))]
       {:quality-level quality
        :noise-sd noise
        :acceptance-probability (/ (double accepted) num-trials)}))))

(defn experiment-reviewers-x-dimensions
  "Interaction: Does adding reviewers help more when evaluating on more dimensions?

   With AND-gating, each dimension is a potential failure point. More reviewers
   might be especially valuable when papers must pass on multiple criteria."
  [& {:keys [reviewer-counts dimension-sets num-runs]
      :or {reviewer-counts [1 2 3 5]
           dimension-sets [[:interest :rigor]
                          [:interest :rigor :contribution]
                          [:interest :rigor :contribution :novelty]]
           num-runs 10}}]
  (vec
   (for [dims dimension-sets
         n reviewer-counts]
     (assoc (run-experiment-n {:num-reviewers n
                               :dimensions dims}
                              num-runs)
            :num-dimensions (count dims)
            :dimensions-label (str (count dims) "D")))))

(defn experiment-threshold-x-noise
  "Interaction: At highly selective journals, does noise matter more or less?

   Hypothesis: Selective journals (high threshold) are deciding among papers
   clustered near the high end of quality, where noise could flip decisions
   more easily. This is particularly relevant for top accounting journals."
  [& {:keys [thresholds noise-levels num-runs]
      :or {thresholds [40 50 60 70]
           noise-levels [15 25 35]
           num-runs 10}}]
  (vec
   (for [thresh thresholds
         noise noise-levels]
     (assoc (run-experiment-n {:threshold thresh
                               :reviewer-noise-sd noise}
                              num-runs)
            :threshold thresh
            :noise-sd noise))))
