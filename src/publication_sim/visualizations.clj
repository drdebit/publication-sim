(ns publication-sim.visualizations
  "Vega-Lite visualizations for the peer review simulation paper.

   All chart functions return Vega-Lite specs that can be rendered with oz/view!
   or exported to JSON/HTML for inclusion in the paper."
  (:require [publication-sim.review-model :as rm]
            [oz.core :as oz]))

;; =============================================================================
;; Color Palette (colorblind-friendly)
;; =============================================================================

(def colors
  {:primary "#2563eb"      ; Blue
   :secondary "#dc2626"    ; Red
   :tertiary "#16a34a"     ; Green
   :quaternary "#9333ea"   ; Purple
   :gray "#6b7280"})

(def color-scheme "tableau10")

;; =============================================================================
;; Chart 1: Accuracy & FNR by Reviewer Count
;; =============================================================================

(defn chart-reviewer-count
  "Dual-axis line chart showing accuracy and FNR by number of reviewers.
   This is the main finding: diminishing returns from adding reviewers."
  [data]
  {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
   :title {:text "Effect of Reviewer Count on Decision Quality"
           :fontSize 16}
   :width 500
   :height 300
   :data {:values (mapv (fn [r]
                          {:reviewers (:num-reviewers r)
                           :accuracy (* 100 (:accuracy r))
                           :fnr (* 100 (:false-negative-rate r))
                           :fpr (* 100 (:false-positive-rate r))})
                        data)}
   :encoding {:x {:field "reviewers"
                  :type "ordinal"
                  :title "Number of Reviewers"
                  :axis {:labelAngle 0}}}
   :layer [{:mark {:type "line" :point true :color (:primary colors)}
            :encoding {:y {:field "accuracy"
                           :type "quantitative"
                           :title "Accuracy (%)"
                           :scale {:domain [70 90]}}}}
           {:mark {:type "line" :point true :strokeDash [5 5] :color (:secondary colors)}
            :encoding {:y {:field "fnr"
                           :type "quantitative"
                           :title "False Negative Rate (%)"
                           :axis {:title "FNR (%)" :titleColor (:secondary colors)}}}}]
   :resolve {:scale {:y "independent"}}})

(defn chart-reviewer-count-simple
  "Single metric (accuracy) by reviewer count - cleaner for some uses."
  [data]
  {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
   :title {:text "Accuracy by Number of Reviewers"
           :fontSize 16}
   :width 450
   :height 300
   :data {:values (mapv (fn [r]
                          {:reviewers (:num-reviewers r)
                           :accuracy (* 100 (:accuracy r))})
                        data)}
   :mark {:type "line" :point {:size 80} :color (:primary colors) :strokeWidth 2}
   :encoding {:x {:field "reviewers"
                  :type "ordinal"
                  :title "Number of Reviewers"
                  :axis {:labelAngle 0}}
              :y {:field "accuracy"
                  :type "quantitative"
                  :title "Accuracy (%)"
                  :scale {:domain [70 90]}}}})

;; =============================================================================
;; Chart 2: Accuracy by Noise Level
;; =============================================================================

(defn chart-noise-level
  "Line chart showing accuracy degradation as reviewer noise increases."
  [data]
  {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
   :title {:text "Effect of Reviewer Noise on Decision Quality"
           :fontSize 16}
   :width 450
   :height 300
   :data {:values (mapv (fn [r]
                          {:noise (:noise-sd r)
                           :accuracy (* 100 (:accuracy r))
                           :fnr (* 100 (:false-negative-rate r))})
                        data)}
   :mark {:type "line" :point {:size 80} :color (:primary colors) :strokeWidth 2}
   :encoding {:x {:field "noise"
                  :type "quantitative"
                  :title "Reviewer Noise (SD)"
                  :scale {:domain [10 40]}}
              :y {:field "accuracy"
                  :type "quantitative"
                  :title "Accuracy (%)"
                  :scale {:domain [70 100]}}}})

;; =============================================================================
;; Chart 3: Quality vs Volume Strategy Comparison
;; =============================================================================

(defn chart-strategy-comparison
  "Bar chart comparing expected publications under different strategies."
  [data]
  (let [formatted (mapv (fn [[name info]]
                          {:strategy (clojure.core/name name)
                           :expected (:expected-publications info)
                           :papers (:papers info)
                           :quality (:quality info)})
                        data)]
    {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
     :title {:text "Quality vs. Volume: Expected Publications"
             :fontSize 16}
     :width 400
     :height 300
     :data {:values (sort-by :expected > formatted)}
     :mark {:type "bar" :color (:primary colors)}
     :encoding {:x {:field "strategy"
                    :type "nominal"
                    :title "Strategy"
                    :sort {:field "expected" :order "descending"}
                    :axis {:labelAngle -45}}
                :y {:field "expected"
                    :type "quantitative"
                    :title "Expected Publications"
                    :scale {:domain [0 1]}}
                :tooltip [{:field "strategy" :type "nominal"}
                          {:field "papers" :type "quantitative" :title "Papers Submitted"}
                          {:field "quality" :type "quantitative" :title "Quality Level"}
                          {:field "expected" :type "quantitative" :title "E[Publications]" :format ".2f"}]}}))

;; =============================================================================
;; Chart 4: Reviewers × Noise Interaction Heatmap
;; =============================================================================

(defn chart-reviewers-x-noise
  "Heatmap showing accuracy across reviewer count and noise level combinations."
  [data]
  {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
   :title {:text "Interaction: Reviewers × Noise Level"
           :fontSize 16}
   :width 350
   :height 250
   :data {:values (mapv (fn [r]
                          {:reviewers (str (:num-reviewers r))
                           :noise (str "SD=" (:noise-sd r))
                           :accuracy (* 100 (:accuracy r))})
                        data)}
   :mark "rect"
   :encoding {:x {:field "reviewers"
                  :type "ordinal"
                  :title "Number of Reviewers"}
              :y {:field "noise"
                  :type "ordinal"
                  :title "Reviewer Noise"}
              :color {:field "accuracy"
                      :type "quantitative"
                      :title "Accuracy (%)"
                      :scale {:scheme "blues" :domain [70 90]}}
              :tooltip [{:field "reviewers" :type "ordinal" :title "Reviewers"}
                        {:field "noise" :type "ordinal"}
                        {:field "accuracy" :type "quantitative" :format ".1f"}]}})

(defn chart-reviewers-x-noise-lines
  "Line chart version of reviewers × noise interaction."
  [data]
  {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
   :title {:text "Reviewer Count Effect at Different Noise Levels"
           :fontSize 16}
   :width 450
   :height 300
   :data {:values (mapv (fn [r]
                          {:reviewers (:num-reviewers r)
                           :noise (str "Noise SD=" (:noise-sd r))
                           :accuracy (* 100 (:accuracy r))})
                        data)}
   :mark {:type "line" :point {:size 60}}
   :encoding {:x {:field "reviewers"
                  :type "ordinal"
                  :title "Number of Reviewers"
                  :axis {:labelAngle 0}}
              :y {:field "accuracy"
                  :type "quantitative"
                  :title "Accuracy (%)"
                  :scale {:domain [65 90]}}
              :color {:field "noise"
                      :type "nominal"
                      :title "Noise Level"
                      :scale {:scheme color-scheme}}}})

;; =============================================================================
;; Chart 5: Acceptance Probability by Quality (S-curve)
;; =============================================================================

(defn chart-acceptance-probability
  "S-curve showing acceptance probability as a function of paper quality."
  [data]
  {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
   :title {:text "Acceptance Probability by Paper Quality"
           :fontSize 16}
   :width 450
   :height 300
   :data {:values (mapv (fn [r]
                          {:quality (:quality-level r)
                           :probability (* 100 (:acceptance-probability r))})
                        data)}
   :layer [{:mark {:type "line" :point {:size 80} :color (:primary colors) :strokeWidth 2}
            :encoding {:x {:field "quality"
                           :type "quantitative"
                           :title "Paper Quality"
                           :scale {:domain [55 90]}}
                       :y {:field "probability"
                           :type "quantitative"
                           :title "Acceptance Probability (%)"
                           :scale {:domain [0 100]}}}}
           ;; Add reference line at threshold
           {:mark {:type "rule" :strokeDash [5 5] :color (:gray colors)}
            :encoding {:x {:datum 70}}}]})

(defn chart-quality-x-noise
  "Acceptance probability curves at different noise levels.
   Shows that borderline papers are most affected by noise."
  [data]
  {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
   :title {:text "Acceptance Probability: Effect of Noise"
           :fontSize 16}
   :width 450
   :height 300
   :data {:values (mapv (fn [r]
                          {:quality (:quality-level r)
                           :noise (str "Noise SD=" (:noise-sd r))
                           :probability (* 100 (:acceptance-probability r))})
                        data)}
   :layer [{:mark {:type "line" :point {:size 50}}
            :encoding {:x {:field "quality"
                           :type "quantitative"
                           :title "Paper Quality"
                           :scale {:domain [45 95]}}
                       :y {:field "probability"
                           :type "quantitative"
                           :title "Acceptance Probability (%)"
                           :scale {:domain [0 100]}}
                       :color {:field "noise"
                               :type "nominal"
                               :title "Noise Level"
                               :scale {:scheme color-scheme}}}}
           ;; Reference line at threshold
           {:mark {:type "rule" :strokeDash [5 5] :color (:gray colors)}
            :encoding {:x {:datum 70}}}]})

;; =============================================================================
;; Chart 6: FNR by Number of Dimensions (AND-gating)
;; =============================================================================

(defn chart-dimensions
  "Bar chart showing how FNR increases with number of evaluation dimensions."
  [data]
  {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
   :title {:text "AND-Gating Effect: More Dimensions = Higher False Negative Rate"
           :fontSize 16}
   :width 350
   :height 300
   :data {:values (mapv (fn [r]
                          {:dimensions (:label r)
                           :fnr (* 100 (:false-negative-rate r))
                           :fpr (* 100 (:false-positive-rate r))})
                        data)}
   :mark {:type "bar" :color (:secondary colors)}
   :encoding {:x {:field "dimensions"
                  :type "ordinal"
                  :title "Evaluation Dimensions"
                  :axis {:labelAngle 0}}
              :y {:field "fnr"
                  :type "quantitative"
                  :title "False Negative Rate (%)"
                  :scale {:domain [0 80]}}}})

;; =============================================================================
;; Chart 7: Reviewers x Dimensions Interaction
;; =============================================================================

(defn chart-reviewers-x-dimensions
  "Line chart showing how reviewer benefit varies by number of dimensions.
   With AND-gating, more reviewers may be especially valuable for multi-dimensional evaluation."
  [data]
  {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
   :title {:text "Reviewers x Dimensions: AND-Gating Requires More Reviewers"
           :fontSize 16}
   :width 450
   :height 300
   :data {:values (mapv (fn [r]
                          {:reviewers (:num-reviewers r)
                           :dimensions (:dimensions-label r)
                           :accuracy (* 100 (:accuracy r))
                           :fnr (* 100 (:false-negative-rate r))})
                        data)}
   :mark {:type "line" :point {:size 60}}
   :encoding {:x {:field "reviewers"
                  :type "ordinal"
                  :title "Number of Reviewers"
                  :axis {:labelAngle 0}}
              :y {:field "accuracy"
                  :type "quantitative"
                  :title "Accuracy (%)"
                  :scale {:domain [70 95]}}
              :color {:field "dimensions"
                      :type "nominal"
                      :title "Dimensions"
                      :scale {:scheme color-scheme}}}})

(defn chart-reviewers-x-dimensions-fnr
  "Shows FNR reduction by reviewer count across dimensions."
  [data]
  {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
   :title {:text "More Reviewers Reduce False Negatives (Especially with More Dimensions)"
           :fontSize 16}
   :width 450
   :height 300
   :data {:values (mapv (fn [r]
                          {:reviewers (:num-reviewers r)
                           :dimensions (:dimensions-label r)
                           :fnr (* 100 (:false-negative-rate r))})
                        data)}
   :mark {:type "line" :point {:size 60}}
   :encoding {:x {:field "reviewers"
                  :type "ordinal"
                  :title "Number of Reviewers"
                  :axis {:labelAngle 0}}
              :y {:field "fnr"
                  :type "quantitative"
                  :title "False Negative Rate (%)"
                  :scale {:domain [20 75]}}
              :color {:field "dimensions"
                      :type "nominal"
                      :title "Dimensions"
                      :scale {:scheme color-scheme}}}})

;; =============================================================================
;; Chart 8: Threshold x Noise Interaction
;; =============================================================================

(defn chart-threshold-x-noise
  "Heatmap showing accuracy across threshold and noise combinations.
   Particularly relevant for highly selective journals."
  [data]
  {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
   :title {:text "Selectivity x Noise: How Noise Affects Different Journal Types"
           :fontSize 16}
   :width 350
   :height 250
   :data {:values (mapv (fn [r]
                          {:threshold (str "Threshold=" (:threshold r))
                           :noise (str "SD=" (:noise-sd r))
                           :accuracy (* 100 (:accuracy r))
                           :fnr (* 100 (:false-negative-rate r))})
                        data)}
   :mark "rect"
   :encoding {:x {:field "noise"
                  :type "ordinal"
                  :title "Reviewer Noise"}
              :y {:field "threshold"
                  :type "ordinal"
                  :title "Journal Selectivity"}
              :color {:field "fnr"
                      :type "quantitative"
                      :title "FNR (%)"
                      :scale {:scheme "reds" :domain [20 70]}}
              :tooltip [{:field "threshold" :type "ordinal"}
                        {:field "noise" :type "ordinal"}
                        {:field "fnr" :type "quantitative" :format ".1f" :title "FNR (%)"}
                        {:field "accuracy" :type "quantitative" :format ".1f" :title "Accuracy (%)"}]}})

(defn chart-threshold-x-noise-lines
  "Line chart version showing FNR at different selectivity levels as noise increases."
  [data]
  {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
   :title {:text "Selective Journals Are More Sensitive to Noise"
           :fontSize 16}
   :width 450
   :height 300
   :data {:values (mapv (fn [r]
                          {:threshold (str "Threshold=" (:threshold r))
                           :noise (:noise-sd r)
                           :fnr (* 100 (:false-negative-rate r))})
                        data)}
   :mark {:type "line" :point {:size 60}}
   :encoding {:x {:field "noise"
                  :type "quantitative"
                  :title "Reviewer Noise (SD)"}
              :y {:field "fnr"
                  :type "quantitative"
                  :title "False Negative Rate (%)"
                  :scale {:domain [20 70]}}
              :color {:field "threshold"
                      :type "nominal"
                      :title "Selectivity"
                      :scale {:scheme color-scheme}}}})

;; =============================================================================
;; Chart 9: Supplemental - Editor as Reviewer
;; =============================================================================

(defn chart-editor-comparison
  "Grouped bar chart comparing main model vs editor-as-reviewer."
  [data]
  (let [formatted (mapv (fn [r]
                          {:reviewers (str (:num-reviewers r) " reviewers")
                           :model (if (= (:model r) "main")
                                    "Main Model"
                                    (str "Editor as Reviewer (SD=" (:editor-noise-sd r) ")"))
                           :accuracy (* 100 (:accuracy r))})
                        data)]
    {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
     :title {:text "Supplemental: Editor as Partial Reviewer"
             :fontSize 16}
     :width 450
     :height 300
     :data {:values formatted}
     :mark "bar"
     :encoding {:x {:field "reviewers"
                    :type "nominal"
                    :title "Configuration"}
                :y {:field "accuracy"
                    :type "quantitative"
                    :title "Accuracy (%)"
                    :scale {:domain [70 82]}}
                :color {:field "model"
                        :type "nominal"
                        :title "Model"
                        :scale {:scheme color-scheme}}
                :xOffset {:field "model" :type "nominal"}}}))

;; =============================================================================
;; Utility Functions
;; =============================================================================

(defn view!
  "Display a chart in the browser."
  [chart-spec]
  (oz/view! chart-spec))

(defn export-html
  "Export chart to HTML file."
  [chart-spec filepath]
  (oz/export! chart-spec filepath))

(defn generate-all-charts
  "Generate all charts from experiment results.
   Returns a map of chart-name -> vega-lite-spec."
  [{:keys [experiment-1-reviewer-count
           experiment-2-noise-level
           experiment-3-dimensions
           experiment-5-quality-strategy
           experiment-6-strategy-comparison
           supplemental-editor-as-reviewer
           interaction-reviewers-x-noise
           interaction-quality-x-noise
           interaction-reviewers-x-dimensions
           interaction-threshold-x-noise]}]
  {:reviewer-count (chart-reviewer-count experiment-1-reviewer-count)
   :reviewer-count-simple (chart-reviewer-count-simple experiment-1-reviewer-count)
   :noise-level (chart-noise-level experiment-2-noise-level)
   :dimensions (chart-dimensions experiment-3-dimensions)
   :acceptance-probability (chart-acceptance-probability experiment-5-quality-strategy)
   :strategy-comparison (chart-strategy-comparison experiment-6-strategy-comparison)
   :editor-comparison (chart-editor-comparison supplemental-editor-as-reviewer)
   :reviewers-x-noise-heatmap (when interaction-reviewers-x-noise
                                (chart-reviewers-x-noise interaction-reviewers-x-noise))
   :reviewers-x-noise-lines (when interaction-reviewers-x-noise
                              (chart-reviewers-x-noise-lines interaction-reviewers-x-noise))
   :quality-x-noise (when interaction-quality-x-noise
                      (chart-quality-x-noise interaction-quality-x-noise))
   :reviewers-x-dimensions (when interaction-reviewers-x-dimensions
                             (chart-reviewers-x-dimensions interaction-reviewers-x-dimensions))
   :reviewers-x-dimensions-fnr (when interaction-reviewers-x-dimensions
                                 (chart-reviewers-x-dimensions-fnr interaction-reviewers-x-dimensions))
   :threshold-x-noise-heatmap (when interaction-threshold-x-noise
                                (chart-threshold-x-noise interaction-threshold-x-noise))
   :threshold-x-noise-lines (when interaction-threshold-x-noise
                              (chart-threshold-x-noise-lines interaction-threshold-x-noise))})

(defn view-all!
  "Display all charts in browser tabs."
  [charts]
  (doseq [[name spec] charts]
    (when spec
      (println "Viewing:" name)
      (oz/view! spec)
      (Thread/sleep 500))))
