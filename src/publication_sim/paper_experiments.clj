(ns publication-sim.paper-experiments
  "Run all experiments for the TAR paper on peer review simulation.

   Usage:
     lein run -m publication-sim.paper-experiments           ; Run experiments, print tables
     lein run -m publication-sim.paper-experiments --charts  ; Also generate charts"
  (:require [publication-sim.review-model :as rm]
            [publication-sim.visualizations :as viz]
            [clojure.pprint :as pp]))

(def num-runs
  "Number of simulation runs to average for stability.
   50 runs produces estimates stable to ~1 percentage point."
  50)

(def num-trials
  "Number of Monte Carlo trials for acceptance probability estimation.
   10000 trials produces estimates stable to ~0.5 percentage points."
  10000)

(defn- run-with-progress
  "Run experiment with progress indicator."
  [label thunk]
  (print (str label "... ")) (flush)
  (let [result (thunk)]
    (println "done.")
    result))

(defn run-all-experiments
  "Run all experiments and return results map."
  []
  (println "Running all experiments for paper...")
  (println (str "Settings: " num-runs " runs, " num-trials " Monte Carlo trials."))
  (println "This may take several minutes.\n")

  (let [exp1 (run-with-progress "Experiment 1: Reviewer count"
               #(rm/experiment-reviewer-count :num-runs num-runs))
        exp2 (run-with-progress "Experiment 2: Noise level"
               #(rm/experiment-noise-level :num-runs num-runs))
        exp3 (run-with-progress "Experiment 3: Dimensions (AND-gating)"
               #(rm/experiment-dimensions :num-runs num-runs))
        exp4 (run-with-progress "Experiment 4: Threshold/selectivity"
               #(rm/experiment-threshold :num-runs num-runs))
        exp5 (run-with-progress "Experiment 5: Quality/volume strategy"
               #(rm/experiment-quality-strategy :num-trials num-trials))
        exp6 (run-with-progress "Experiment 6: Strategy comparison"
               #(rm/experiment-strategy-comparison :num-trials num-trials))
        int1 (run-with-progress "Interaction: Reviewers x Noise"
               #(rm/experiment-reviewers-x-noise :num-runs num-runs))
        int2 (run-with-progress "Interaction: Quality x Noise"
               #(rm/experiment-quality-x-noise :num-trials num-trials))
        int3 (run-with-progress "Interaction: Reviewers x Dimensions"
               #(rm/experiment-reviewers-x-dimensions :num-runs num-runs))
        int4 (run-with-progress "Interaction: Threshold x Noise"
               #(rm/experiment-threshold-x-noise :num-runs num-runs))
        supp (run-with-progress "Supplemental: Editor as partial reviewer"
               #(rm/experiment-editor-as-reviewer :num-runs num-runs))
        rob1 (run-with-progress "Robustness: ICC sensitivity"
               #(rm/experiment-icc-sensitivity :num-runs num-runs))
        rob2 (run-with-progress "Robustness: Aggregation method"
               #(rm/experiment-aggregation-method :num-runs num-runs))
        rob3 (run-with-progress "Robustness: Correlated errors"
               #(rm/experiment-correlated-errors :num-runs num-runs))
        rob4 (run-with-progress "Robustness: Quality variance"
               #(rm/experiment-quality-variance :num-runs num-runs))
        rob5 (run-with-progress "Robustness: Extended strategies"
               #(rm/experiment-extended-strategies :num-trials num-trials))
        rob6 (run-with-progress "Robustness: Noise crossover"
               #(rm/experiment-noise-crossover :num-trials num-trials))
        rob7 (run-with-progress "Robustness: Dimension correlation"
               #(rm/experiment-dimension-correlation :num-runs num-runs))]

    (println)

    {:experiment-1-reviewer-count exp1
     :experiment-2-noise-level exp2
     :experiment-3-dimensions exp3
     :experiment-4-threshold exp4
     :experiment-5-quality-strategy exp5
     :experiment-6-strategy-comparison exp6
     :interaction-reviewers-x-noise int1
     :interaction-quality-x-noise int2
     :interaction-reviewers-x-dimensions int3
     :interaction-threshold-x-noise int4
     :supplemental-editor-as-reviewer supp
     :robustness-icc-sensitivity rob1
     :robustness-aggregation-method rob2
     :robustness-correlated-errors rob3
     :robustness-quality-variance rob4
     :robustness-extended-strategies rob5
     :robustness-noise-crossover rob6
     :robustness-dimension-correlation rob7}))

(defn print-results
  "Print formatted results for all experiments."
  [results]

  (println "\n" (apply str (repeat 70 "=")) "\n")
  (println "EXPERIMENT 1: Effect of Reviewer Count")
  (println (apply str (repeat 70 "-")))
  (println "Reviewers | FNR    | FPR    | Accuracy | Reliability | Quality Gap")
  (println (apply str (repeat 70 "-")))
  (doseq [r (:experiment-1-reviewer-count results)]
    (println (format "    %2d    | %.3f  | %.3f  |  %.3f   |    %.3f    |   %.1f"
                     (:num-reviewers r)
                     (:false-negative-rate r)
                     (:false-positive-rate r)
                     (:accuracy r)
                     (or (:reliability r) 0.0)
                     (:quality-gap r))))

  (println "\n" (apply str (repeat 70 "=")) "\n")
  (println "EXPERIMENT 2: Effect of Reviewer Noise (SD)")
  (println (apply str (repeat 70 "-")))
  (println "Noise SD | FNR    | FPR    | Accuracy | Reliability")
  (println (apply str (repeat 70 "-")))
  (doseq [r (:experiment-2-noise-level results)]
    (println (format "   %2d    | %.3f  | %.3f  |  %.3f   |    %.3f"
                     (:noise-sd r)
                     (:false-negative-rate r)
                     (:false-positive-rate r)
                     (:accuracy r)
                     (or (:reliability r) 0.0))))

  (println "\n" (apply str (repeat 70 "=")) "\n")
  (println "EXPERIMENT 3: Effect of Evaluation Dimensions (AND-gating)")
  (println (apply str (repeat 70 "-")))
  (println "Dimensions | FNR    | FPR    | Accuracy")
  (println (apply str (repeat 70 "-")))
  (doseq [r (:experiment-3-dimensions results)]
    (println (format "%-14s | %.3f  | %.3f  |  %.3f"
                     (:label r)
                     (:false-negative-rate r)
                     (:false-positive-rate r)
                     (:accuracy r))))

  (println "\n" (apply str (repeat 70 "=")) "\n")
  (println "EXPERIMENT 4: Effect of Journal Selectivity (Threshold)")
  (println (apply str (repeat 70 "-")))
  (println "Threshold | FNR    | FPR    | Accuracy")
  (println (apply str (repeat 70 "-")))
  (doseq [r (:experiment-4-threshold results)]
    (println (format "    %2d    | %.3f  | %.3f  |  %.3f"
                     (:threshold r)
                     (:false-negative-rate r)
                     (:false-positive-rate r)
                     (:accuracy r))))

  (println "\n" (apply str (repeat 70 "=")) "\n")
  (println "EXPERIMENT 5: Acceptance Probability by Paper Quality")
  (println (apply str (repeat 70 "-")))
  (println "Quality Level | Acceptance Probability")
  (println (apply str (repeat 70 "-")))
  (doseq [r (:experiment-5-quality-strategy results)]
    (println (format "     %2d       |        %.1f%%"
                     (:quality-level r)
                     (* 100 (:acceptance-probability r)))))

  (println "\n" (apply str (repeat 70 "=")) "\n")
  (println "EXPERIMENT 6: Quality vs Volume Strategy Comparison")
  (println "(Threshold=70, 2 reviewers, noise SD=30)")
  (println (apply str (repeat 70 "-")))
  (println "Strategy         | Papers | Quality | P(Accept) | E[Pubs]")
  (println (apply str (repeat 70 "-")))
  (doseq [[name data] (sort-by #(- (:expected-publications (second %)))
                               (:experiment-6-strategy-comparison results))]
    (println (format "%-16s |   %d    |   %2d    |   %.1f%%   |  %.2f"
                     (clojure.core/name name)
                     (:papers data)
                     (:quality data)
                     (* 100 (:prob-accept data))
                     (:expected-publications data))))

  (println "\n" (apply str (repeat 70 "=")) "\n")
  (println "INTERACTION: Reviewers x Noise")
  (println "(Does adding reviewers help more when noise is high?)")
  (println (apply str (repeat 70 "-")))
  (println "Noise SD | Reviewers | Accuracy | FNR")
  (println (apply str (repeat 70 "-")))
  (doseq [r (sort-by (juxt :noise-sd :num-reviewers)
                     (:interaction-reviewers-x-noise results))]
    (println (format "   %2d    |     %d     |  %.3f   | %.3f"
                     (:noise-sd r)
                     (:num-reviewers r)
                     (:accuracy r)
                     (:false-negative-rate r))))

  (println "\n" (apply str (repeat 70 "=")) "\n")
  (println "INTERACTION: Quality x Noise")
  (println "(Acceptance probability at different quality levels and noise)")
  (println (apply str (repeat 70 "-")))
  (println "Quality | Noise SD=15 | Noise SD=30 | Noise SD=45")
  (println (apply str (repeat 70 "-")))
  (let [grouped (group-by :quality-level (:interaction-quality-x-noise results))]
    (doseq [quality (sort (keys grouped))]
      (let [by-noise (into {} (map (fn [r] [(:noise-sd r) r]) (get grouped quality)))]
        (println (format "   %2d   |    %.1f%%    |    %.1f%%    |    %.1f%%"
                         quality
                         (* 100 (:acceptance-probability (get by-noise 15)))
                         (* 100 (:acceptance-probability (get by-noise 30)))
                         (* 100 (:acceptance-probability (get by-noise 45))))))))

  (println "\n" (apply str (repeat 70 "=")) "\n")
  (println "INTERACTION: Reviewers x Dimensions")
  (println "(Does adding reviewers help more with multi-dimensional evaluation?)")
  (println (apply str (repeat 70 "-")))
  (println "Dimensions | Reviewers | Accuracy | FNR    | FPR")
  (println (apply str (repeat 70 "-")))
  (doseq [r (sort-by (juxt :num-dimensions :num-reviewers)
                     (:interaction-reviewers-x-dimensions results))]
    (println (format "    %s      |     %d     |  %.3f   | %.3f  | %.3f"
                     (:dimensions-label r)
                     (:num-reviewers r)
                     (:accuracy r)
                     (:false-negative-rate r)
                     (:false-positive-rate r))))

  (println "\n" (apply str (repeat 70 "=")) "\n")
  (println "INTERACTION: Threshold x Noise")
  (println "(At selective journals, does noise matter more?)")
  (println (apply str (repeat 70 "-")))
  (println "Threshold | Noise SD | Accuracy | FNR    | FPR")
  (println (apply str (repeat 70 "-")))
  (doseq [r (sort-by (juxt :threshold :noise-sd)
                     (:interaction-threshold-x-noise results))]
    (println (format "    %2d    |    %2d    |  %.3f   | %.3f  | %.3f"
                     (:threshold r)
                     (:noise-sd r)
                     (:accuracy r)
                     (:false-negative-rate r)
                     (:false-positive-rate r))))

  (println "\n" (apply str (repeat 70 "=")) "\n")
  (println "SUPPLEMENTAL: Editor as Partial Reviewer")
  (println "(Does adding editor's assessment to reviewer average improve accuracy?)")
  (println (apply str (repeat 70 "-")))
  (println "Model                | Reviewers | Editor SD | FNR    | Accuracy")
  (println (apply str (repeat 70 "-")))
  (doseq [r (sort-by (juxt :num-reviewers :model :editor-noise-sd)
                     (:supplemental-editor-as-reviewer results))]
    (println (format "%-20s |     %d     |    %-4s   | %.3f  |  %.3f"
                     (:model r)
                     (:num-reviewers r)
                     (if (:editor-noise-sd r)
                       (str (:editor-noise-sd r))
                       "n/a")
                     (:false-negative-rate r)
                     (:accuracy r))))

  (println "\n" (apply str (repeat 70 "=")) "\n")
  (println "ROBUSTNESS: ICC Sensitivity")
  (println (apply str (repeat 70 "-")))
  (println "  ICC  | Noise SD | FNR    | FPR    | Accuracy")
  (println (apply str (repeat 70 "-")))
  (doseq [r (:robustness-icc-sensitivity results)]
    (println (format "  %.2f |   %.1f   | %.3f  | %.3f  |  %.3f"
                     (double (:icc r))
                     (double (:implied-noise-sd r))
                     (:false-negative-rate r)
                     (:false-positive-rate r)
                     (:accuracy r))))

  (println "\n" (apply str (repeat 70 "=")) "\n")
  (println "ROBUSTNESS: Mean vs Median Aggregation")
  (println (apply str (repeat 70 "-")))
  (println "Reviewers | Method | Accuracy | FNR")
  (println (apply str (repeat 70 "-")))
  (doseq [r (sort-by (juxt :num-reviewers :aggregation-method)
                     (:robustness-aggregation-method results))]
    (println (format "    %d     | %-6s |  %.3f   | %.3f"
                     (:num-reviewers r)
                     (name (:aggregation-method r))
                     (:accuracy r)
                     (:false-negative-rate r))))

  (println "\n" (apply str (repeat 70 "=")) "\n")
  (println "ROBUSTNESS: Correlated Reviewer Errors")
  (println (apply str (repeat 70 "-")))
  (println "Correlation | Reviewers | Accuracy | FNR")
  (println (apply str (repeat 70 "-")))
  (doseq [r (sort-by (juxt :correlation :num-reviewers)
                     (:robustness-correlated-errors results))]
    (println (format "    %.2f    |     %d     |  %.3f   | %.3f"
                     (double (:correlation r))
                     (:num-reviewers r)
                     (:accuracy r)
                     (:false-negative-rate r))))

  (println "\n" (apply str (repeat 70 "=")) "\n")
  (println "ROBUSTNESS: Quality Variance (tau)")
  (println (apply str (repeat 70 "-")))
  (println "Quality SD | ICC  | FNR    | FPR    | Accuracy")
  (println (apply str (repeat 70 "-")))
  (doseq [r (:robustness-quality-variance results)]
    (println (format "    %2d     | %.2f | %.3f  | %.3f  |  %.3f"
                     (:quality-sd r)
                     (double (:implied-icc r))
                     (:false-negative-rate r)
                     (:false-positive-rate r)
                     (:accuracy r))))

  (println "\n" (apply str (repeat 70 "=")) "\n")
  (println "ROBUSTNESS: Extended Strategy Comparison")
  (println "(Including below-threshold lottery strategies)")
  (println (apply str (repeat 70 "-")))
  (println "Strategy         | Papers | Quality | P(Accept) | E[Pubs]")
  (println (apply str (repeat 70 "-")))
  (doseq [r (sort-by #(- (:expected-publications %))
                     (:robustness-extended-strategies results))]
    (println (format "%-16s |   %2d   |   %2d    |   %5.1f%%  |  %.3f"
                     (:strategy r)
                     (:papers r)
                     (:quality r)
                     (* 100 (:p-accept r))
                     (:expected-publications r))))

  (println "\n" (apply str (repeat 70 "=")) "\n")
  (println "ROBUSTNESS: Noise Crossover Analysis")
  (println "(Where does noise switch from helping to hurting?)")
  (println (apply str (repeat 70 "-")))
  (println "Quality | P(Low Noise) | P(High Noise) | Delta   | Effect")
  (println (apply str (repeat 70 "-")))
  (doseq [r (filter #(#{50 58 64 70 76 84 90} (:quality %))
                    (:robustness-noise-crossover results))]
    (println (format "   %2d   |    %5.1f%%    |    %5.1f%%     | %+5.1f%%  | %s"
                     (:quality r)
                     (* 100 (:p-accept-low-noise r))
                     (* 100 (:p-accept-high-noise r))
                     (* 100 (:delta r))
                     (name (:noise-effect r)))))

  (println "\n" (apply str (repeat 70 "=")) "\n")
  (println "ROBUSTNESS: Dimension Correlation")
  (println "(Does correlated quality across dimensions reduce AND-gate effect?)")
  (println (apply str (repeat 70 "-")))
  (println "Dim Correlation | FNR    | FPR    | Accuracy")
  (println (apply str (repeat 70 "-")))
  (doseq [r (:robustness-dimension-correlation results)]
    (println (format "      %.1f       | %.3f  | %.3f  |  %.3f"
                     (double (:dimension-correlation r))
                     (:false-negative-rate r)
                     (:false-positive-rate r)
                     (:accuracy r))))

  (println "\n" (apply str (repeat 70 "=")) "\n")
  (println "KEY FINDINGS:")
  (println "1. Going from 1->2 reviewers provides largest accuracy gain")
  (println "2. Accounting journals (1-2 reviewers) leave significant accuracy on table")
  (println "3. AND-gating across dimensions increases false negative rate")
  (println "4. Noisy review creates incentive to favor quantity over quality")
  (println "5. More reviewers help MORE when noise is high (reviewers x noise)")
  (println "6. Borderline papers most affected by noise (quality x noise)")
  (println "7. More reviewers help MORE with multi-dimensional evaluation (reviewers x dimensions)")
  (println "8. At selective journals, noise has larger effect on FNR (threshold x noise)")
  (println "9. Adding editor's assessment to average REDUCES accuracy (supplemental)"))

(defn generate-charts
  "Generate all visualizations from experiment results."
  [results]
  (println "\nGenerating charts...")
  (let [charts (viz/generate-all-charts results)]
    (println "Generated" (count (filter val charts)) "charts.")
    (println "\nTo view charts, use (viz/view! chart-spec) in the REPL")
    (println "Available charts:" (keys charts))
    charts))

(defn export-charts
  "Export all charts to HTML files in the specified directory."
  [charts output-dir]
  (println (str "\nExporting charts to " output-dir "..."))
  (doseq [[name spec] charts
          :when spec]
    (let [filepath (str output-dir "/" (clojure.core/name name) ".html")]
      (viz/export-html spec filepath)
      (println "  Exported:" filepath)))
  (println "Done."))

(defn -main
  "Entry point for running paper experiments.

   Options:
     --charts     Generate and display charts
     --export DIR Export charts to HTML files in DIR"
  [& args]
  (let [results (run-all-experiments)
        show-charts? (some #{"--charts"} args)
        export-dir (when-let [idx (some #(when (= "--export" (first %)) (second %))
                                        (partition 2 1 args))]
                     idx)]
    (print-results results)

    (when (or show-charts? export-dir)
      (let [charts (generate-charts results)]
        (when show-charts?
          (println "\nOpening charts in browser...")
          (viz/view-all! charts))
        (when export-dir
          (export-charts charts export-dir))))

    (println "\nExperiment complete.")
    results))
