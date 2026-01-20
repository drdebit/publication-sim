(ns publication-sim.paper-experiments
  "Run all experiments for the TAR paper on peer review simulation.

   Usage:
     lein run -m publication-sim.paper-experiments           ; Run experiments, print tables
     lein run -m publication-sim.paper-experiments --charts  ; Also generate charts"
  (:require [publication-sim.review-model :as rm]
            [publication-sim.visualizations :as viz]
            [clojure.pprint :as pp]))

(def num-runs
  "Number of simulation runs to average for stability."
  20)

(defn run-all-experiments
  "Run all experiments and return results map."
  []
  (println "Running all experiments for paper...")
  (println "This may take a few minutes.\n")

  (print "Experiment 1: Reviewer count... ") (flush)
  (let [exp1 (rm/experiment-reviewer-count :num-runs num-runs)]
    (println "done.")

    (print "Experiment 2: Noise level... ") (flush)
    (let [exp2 (rm/experiment-noise-level :num-runs num-runs)]
      (println "done.")

      (print "Experiment 3: Dimensions (AND-gating)... ") (flush)
      (let [exp3 (rm/experiment-dimensions :num-runs num-runs)]
        (println "done.")

        (print "Experiment 4: Threshold/selectivity... ") (flush)
        (let [exp4 (rm/experiment-threshold :num-runs num-runs)]
          (println "done.")

          (print "Experiment 5: Quality/volume strategy... ") (flush)
          (let [exp5 (rm/experiment-quality-strategy)]
            (println "done.")

            (print "Experiment 6: Strategy comparison... ") (flush)
            (let [exp6 (rm/experiment-strategy-comparison)]
              (println "done.")

              (print "Interaction: Reviewers x Noise... ") (flush)
              (let [int1 (rm/experiment-reviewers-x-noise :num-runs num-runs)]
                (println "done.")

                (print "Interaction: Quality x Noise... ") (flush)
                (let [int2 (rm/experiment-quality-x-noise)]
                  (println "done.")

                  (print "Interaction: Reviewers x Dimensions... ") (flush)
                  (let [int3 (rm/experiment-reviewers-x-dimensions :num-runs num-runs)]
                    (println "done.")

                    (print "Interaction: Threshold x Noise... ") (flush)
                    (let [int4 (rm/experiment-threshold-x-noise :num-runs num-runs)]
                      (println "done.")

                      (print "Supplemental: Editor as partial reviewer... ") (flush)
                      (let [supp (rm/experiment-editor-as-reviewer :num-runs num-runs)]
                        (println "done.\n")

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
                         :supplemental-editor-as-reviewer supp}))))))))))))

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
