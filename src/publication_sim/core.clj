(ns publication-sim.core
  (:gen-class)
  (:require [fastmath.random :as r]
            [fastmath.stats :as stats]
            [clojure.core.reducers :as reducers]
            [oz.core :as oz]))

(def num-journals 10)
(def num-researchers 100)
(def max-papers nil)

(defn eu [prob value]
  (* prob value))

(defn uuid [] (.toString (java.util.UUID/randomUUID)))

;; Note: rand() draws from a uniform distribution.
(defn mosquito-maker []
  (let [uuid (uuid)
        interest (* 50 (rand))
        rigor (* 50 (rand))]
    [uuid {:rid uuid
           :resources (* 3 interest rigor)
           :interest interest
           :rigor rigor}]))

(defn journal-maker []
  (let [uuid (uuid)]
    {:jid uuid
     :threshold (* 100 (rand))}))

(defn swarm [n]
  (into {} (repeatedly n mosquito-maker)))

(defn journals [n]
  (into [] (repeatedly n journal-maker)))

(defn paper
  ([p]
   (into (select-keys p [:interest :rigor]) {:pid (uuid)}))
  ([m v]
   (let [p (get-in m v)]
     (paper p))))

(defn value [p]
  (reduce + (vals (select-keys p [:interest :rigor]))))

(defn paper-value [m & path]
  (let [p (cond
            path (paper m path)
            :else (paper m))]
    (value p)))

(defn max-accept [submissions-for-journal]
  (take (or max-papers (/ num-researchers num-journals)) submissions-for-journal))

(defn reverse-sort-by-paper-value [submissions-for-journal]
  (reverse
   (sort-by
    (fn [r] (paper-value r :paper))
    submissions-for-journal)))

(defn apply-threshold [submissions-for-journal]
  (filterv (fn [s] (>= (paper-value s :paper) (:threshold s))) submissions-for-journal))

(defn avg-paper-value [papers]
  (cond
    (empty? papers) 0
    :else (/ (apply + (map (fn [p] (paper-value p :paper)) papers)) (count papers))))

(defn noisy-paper-value
  [p noise-sd]
  (let [true-value (value p)]
    (+ true-value (r/sample (r/distribution :normal {:mu 0 :sd noise-sd})))))

(defn apply-noisy-threshold [submissions noise-sd]
  (filterv (fn [s]
             (let [threshold (:threshold s)
                   noisy-val (if (> noise-sd 0)
                               (noisy-paper-value (:paper s) noise-sd)
                               (paper-value s :paper))]
               (>= noisy-val threshold)))
           submissions))

(defn make-noisy-select [noise-sd]
  (comp
    max-accept
    reverse-sort-by-paper-value
    #(apply-noisy-threshold % noise-sd)))

(def default-params
  {:num-journals 10
   :num-researchers 100
   :select-fn (comp max-accept reverse-sort-by-paper-value apply-threshold)
   :payoff-fn avg-paper-value})

(defn init
  ([] (init {}))
  ([overrides]
   (let [{:keys [num-journals num-researchers select-fn payoff-fn]
          :or {num-journals (:num-journals default-params)
               num-researchers (:num-researchers default-params)
               select-fn (:select-fn default-params)
               payoff-fn (:payoff-fn default-params)}} overrides]
     {:journals (journals num-journals)
      :researchers (swarm num-researchers)
      :select-fn select-fn
      :payoff-fn payoff-fn})))

(def ws (init))

(defn normal-maker
  ([mean]
   (r/distribution :normal {:mu mean}))
  ([mean sd]
   (r/distribution :normal {:mu mean :sd sd})))

(defn cdf-maker
  ([mean value]
   (r/cdf (r/distribution :normal {:mu mean}) value))
  ([mean sd value]
   (r/cdf (r/distribution :normal {:mu mean :sd sd}) value)))

(def cdf-memo (memoize cdf-maker))

(defn assess-journal-probability [j p]
  (let [val-p (value p)
        {jid :jid
         threshold :threshold} j]
    (cond
      (contains? j :last-payoff)
      {:jid jid
       :threshold threshold
       :eu (let [{mean :Mean
                  sd :SD} (stats/stats-map (:last-payoff j))]
             (cond
               (or (nil? sd) (<= sd 0)) (eu (cdf-memo mean val-p) mean)
               :else (eu (cdf-memo mean sd val-p) mean)))}
      :else {:jid jid :threshold threshold :eu (eu (cdf-memo 50 val-p) 50)})))

(defn rank-journals [js p]
  (->> js
       (reducers/map #(assess-journal-probability % p))
       (group-by :eu)
       (sort-by first)
       reverse))

(defn determine-r-cost [r]
  (cond
    (:rejections r) 0
    :else (paper-value r)))

(def test-fn (fn [r] r))

(defn researcher-cost
  ([researchers]
   (into {}
         (reducers/map (fn [[k r]] [k
                            (-> r
                                ((fn [r]
                                   (if (= (count (:rejections r)) num-journals)
                                     (dissoc r :rejections)
                                     r)))
                                (update :resources #(- % (determine-r-cost r))))]) researchers)))
  ([researchers set-cost]
   (into {}
         (mapv (fn [[k v]] [k (update v :resources #(- % set-cost))]) researchers))))

(defn researcher-payoff [r p]
  (-> r (update :resources #(+ % p)) (dissoc :rejections)))

(defn submit-to-journals [ws]
  (let [{journals :journals
         researchers :researchers} ws]
    (assoc ws :submissions-by-journal
           (->> researchers
                (reducers/map (fn [[_ r]]
                                (let [rp (paper r)
                                      journals (filter (fn [j]
                                                         (not (contains? (:rejections r) (:jid j))
                                                              )) journals)
                                      selected-utility (first (rank-journals journals rp))
                                      js (first (shuffle (second selected-utility)))]
                                  (-> js
                                      (conj {:paper rp
                                             :rid (:rid r)})))))
                (group-by :jid)))))

(defn submissions [ws]
  (-> ws
   (update :researchers researcher-cost)
   (update :researchers (fn [rs] (remove (fn [[_ v]] (< (:resources v) 0)) rs)))
   submit-to-journals))

(defn journal-selection [ws]
  (let [select-fn (:select-fn ws)]
    (assoc ws :accepted (update-vals (:submissions-by-journal ws) select-fn))))

(defn submissions-by-researcher [s-by-j]
  (update-vals (->> (vals s-by-j)
               flatten
               (group-by :rid)
               ) first))

(defn payoffs [ws]
  (let [{accepted :accepted
         journals :journals
         submissions-by-journal :submissions-by-journal
         payoff-fn :payoff-fn} ws
        submissions-by-researcher (submissions-by-researcher submissions-by-journal)
        jpay-fn (fn [j] (let [jid (:jid j)]
                          (payoff-fn (get-in ws [:accepted jid]))))]
    (-> ws
        (update :researchers
                (fn [rs]
                  (update-vals
                   rs
                   (fn [r]
                     (let [rid (:rid r)
                           journal-payoffs
                           (into {}
                                 (reducers/map (fn [j]
                                        (let [payoff (jpay-fn j)
                                              jid (:jid j)
                                              accepted-papers (get accepted jid)]
                                          (into {} (map (fn [p] [(:rid p) payoff]) accepted-papers)))) journals))]
                       (cond
                         (contains? journal-payoffs rid) (researcher-payoff r (get journal-payoffs rid))
                         :else (update r :rejections (fnil (fn [hs]
                                                             (conj hs (get-in submissions-by-researcher [rid :jid]))) #{}))))))))
        (update :journals (fn [jl]
                            (into []
                                  (reducers/map (fn [j]
                                                  (let [payoff (jpay-fn j)]
                                                    (cond
                                                      (> payoff 0) (assoc j :last-payoff (jpay-fn j))
                                                      :else (dissoc j :last-payoff)))) jl)))))))

;; Run
(defn run [ws]
  (-> ws
      submissions
      journal-selection
      payoffs
      ))

;; (def runs (take 200 (iterate run ws)))

(defn review-quality-correlation [ws]
  (let [accepted-papers (->> (:accepted ws) vals flatten)
        qualities (map #(paper-value % :paper) accepted-papers)
        payoffs (map (fn [p]
                       (get-in ws [:researchers (:rid p) :resources])) accepted-papers)]
    (if (and (seq qualities) (seq payoffs)
             (> (count qualities) 1)
             (not= (apply min qualities) (apply max qualities))
             (not= (apply min payoffs) (apply max payoffs)))
      (stats/correlation qualities payoffs)
      ;; Uncomment for debugging
      (do (println "Invalid correlation input:" qualities payoffs) nil))))

(defn avg-rejections-per-researcher [ws]
  (let [researchers (vals (:researchers ws))
        rejection-counts (map #(count (get % :rejections #{})) researchers)]
    (if (seq rejection-counts)
      (double (/ (apply + rejection-counts) (count rejection-counts)))
      0)))

(defn journal-acceptance-ratios [ws]
  (for [j (:journals ws)]
    (let [jid (:jid j)
          submissions (count (get-in ws [:submissions-by-journal jid]))
          acceptances (count (get-in ws [:accepted jid]))]
      {:jid jid
       :submissions submissions
       :acceptances acceptances
       :acceptance-rate (if (pos? submissions)
                          (/ acceptances submissions)
                          0)})))

(defn avg-acceptance-rate [ws]
  (let [ratios (journal-acceptance-ratios ws)
        rates (map :acceptance-rate ratios)]
    (if (seq rates)
      (double (/ (apply + rates) (count rates)))
      0)))

(defn mean-paper-quality [papers]
  (if (seq papers)
    (/ (apply + (map #(paper-value % :paper) papers))
       (count papers))
    0))

(defn acceptance-quality-stats [ws]
  (let [accepted (->> (:accepted ws) vals flatten)
        rejected (let [s-by-j (get ws :submissions-by-journal)
                       accepted-set (set (map :pid accepted))]
                   (->> (vals s-by-j)
                        flatten
                        (remove #(contains? accepted-set (:pid (:paper %))))))]
    {:mean-quality-accepted (mean-paper-quality accepted)
     :mean-quality-rejected (mean-paper-quality rejected)}))

;; (def metrics-per-run
;;   (map-indexed
;;    (fn [i ws]
;;      (let [{:keys [mean-quality-accepted mean-quality-rejected]} (acceptance-quality-stats ws)]
;;        {:step i
;;         :correlation (review-quality-correlation ws)
;;         :avg-rejections (avg-rejections-per-researcher ws)
;;         :avg-acceptance-rate (avg-acceptance-rate ws)
;;         :mean-quality-accepted mean-quality-accepted
;;         :mean-quality-rejected mean-quality-rejected}))
;;    runs))

;; Visualize
(defn play-data [& names]
  (for [n names
        i (range 20)]
    {:time i :item n :quantity (+ (Math/pow (* i (count n)) 0.8) (rand-int (count n)))}))

(def line-plot
  {:data {:values (play-data "monkey" "slipper" "broom")}
   :encoding {:x {:field "time" :type "quantitative"}
              :y {:field "quantity" :type "quantitative"}
              :color {:field "item" :type "nominal"}}
   :mark "line"})

;; (def qualcorr-line-plot
;;   {:data {:values (map-indexed
;;                    (fn [idx run]
;;                      {:index idx :correlation (review-quality-correlation run)}) runs)}
;;    :encoding {:x {:field "index" :type "quantitative"}
;;               :y {:field "correlation" :type "quantitative"}}
;;    :mark "line"})

;; (def avg-rej-line-plot
;;   {:data {:values (vec (map-indexed
;;                         (fn [idx run]
;;                           {:index idx :avg_reject (avg-rejections-per-researcher run)}) runs))}
;;    :encoding {:x {:field "index" :type "quantitative"}
;;               :y {:field "avg_reject" :type "quantitative"}}
;;    :mark "line"})

;; (def corr-vs-rejection-plot
;;   {:data {:values metrics-per-run}
;;    :layer [{:mark "line"
;;             :encoding {:x {:field "step" :type "quantitative"}
;;                        :y {:field "correlation" :type "quantitative"}
;;                        :color {:value "blue"}}}
;;            {:mark "line"
;;             :encoding {:x {:field "step" :type "quantitative"}
;;                        :y {:field "avg-rejections" :type "quantitative"}
;;                        :color {:value "red"}}}
;;            {:mark "line"
;;             :encoding {:x {:field "step" :type "quantitative"}
;;                        :y {:field "avg-rejections" :type "quantitative"}
;;                        :color {:value "green"}}}
;;            {:mark "line"
;;             :encoding {:x {:field "step" :type "quantitative"}
;;                        :y {:field "mean-quality-accepted" :type "quantitative"
;;                            :axis {:title "Mean Quality Accepted"}
;;                            :scale {:zero false}}
;;                        :y2 {:field "mean-quality-accepted"}
;;                        :color {:value "purple"}}}]})

;; (def dual-axis-plot
;;   {:data {:values (vec metrics-per-run)}
;;    :layer [{:mark {:type "line" :color "steelblue"}
;;             :encoding {:x {:field "step" :type "quantitative"}
;;                        :y {:field "correlation" :type "quantitative"
;;                             :axis {:title "Correlation" :titleColor "steelblue" :labelColor "steelblue" :grid false}}}}

;;            {:mark {:type "line" :color "firebrick"}
;;             :encoding {:x {:field "step" :type "quantitative"}
;;                        :y {:field "mean-quality-accepted" :type "quantitative"
;;                            :axis {:title "Mean Quality Accepted"
;;                                    :titleColor "firebrick"
;;                                    :labelColor "firebrick"
;;                                    :orient "right"
;;                                    :grid false}}}}]
;;    :resolve {:scale {:y "independent"}}})

;; (oz/view! dual-axis-plot)


;; (def pd (play-data "monkey" "slipper" "broom"))

;; (oz/view! line-plot)

;; (defn reshape []
;;   (map-indexed (fn [idx run] (mapv (fn [[k v]] {:index idx :r k :resources (:resources v)}) (:researchers run))) runs))

;; (defn reshape-journals []
;;   (map-indexed (fn [idx run] (mapv (fn [m] (into m {:index idx}))
;;                                    (:journals run))) runs))

;; (def jline-plot
;;   {:data {:values (flatten (reshape-journals))}
;;    :encoding {:row {:field "index" :type "nominal"}
;;               :x {:field "jid" :type "nominal"}
;;               :y {:field "last-payoff" :type "quantitative" :impute {:value 0}}}
;;    :mark "bar"})

;; (def rline-plot
;;   {:data {:values (flatten (reshape))}
;;    :encoding {:row {:field "index" :type "nominal"}
;;               :x {:field "r" :type "nominal"}
;;               :y {:field "resources" :type "quantitative"}}
;;    :mark "bar"})

;; (def sline-plot
;;   {:data {:values (flatten (reshape))}
;;    :encoding {:row {:field "index" :type "nominal"}
;;               :x {:field "s" :type "nominal"}
;;               :y {:field "resources" :type "quantitative"}}
;;    :mark "bar"})

;; ;; (oz/view! rline-plot)
;; ;; (oz/view! jline-plot)


;; ;; Run experiments
;; (def experiments
;;   [{:config-id "baseline"
;;     :select-fn (comp max-accept reverse-sort-by-paper-value apply-threshold)}

;;    {:config-id "noisy-15"
;;     :select-fn (make-noisy-select 15)}

;;    {:config-id "noisy-30"
;;     :select-fn (make-noisy-select 30)}

;;    {:config-id "noisy-45"
;;     :select-fn (make-noisy-select 45)}

;;    {:config-id "noisy-60"
;;     :select-fn (make-noisy-select 60)}])

;; (defn run-experiment [params steps]
;;   (let [initial (init params)
;;         run-seq (take steps (iterate run initial))]
;;     (map-indexed
;;      (fn [i ws]
;;        (merge
;;         {:step i
;;          :config-id (:config-id params)}
;;         (acceptance-quality-stats ws)
;;         {:correlation (review-quality-correlation ws)
;;          :avg_rejections (avg-rejections-per-researcher ws)
;;          :avg_acceptance_rate (avg-acceptance-rate ws)}))
;;      run-seq)))

;; (defn run-all-experiments [configs steps]
;;   (vec (mapcat #(run-experiment % steps) configs)))

;; (def all-experiment-results
;;   (run-all-experiments experiments 200))

;; (def experiment-facet-plot
;;   {:data {:values all-experiment-results}
;;    :facet {:column {:field "config-id" :type "nominal"}}
;;    :spec {:mark "line"
;;           :encoding {:x {:field "step" :type "quantitative"}
;;                      :y {:field "mean-quality-rejected" :type "quantitative"}}}})

;; (oz/view! experiment-facet-plot)

;; ;; Noise experiment
;; (def noise-levels [0 15 30 45 60])

;; (defn run-noise-experiment [noise-level steps]
;;   (let [config {:select-fn (make-noisy-select noise-level)
;;                 :config-id (str "noise-" noise-level)}
;;         run-seq (take steps (iterate run (init config)))]
;;     (let [final-ws (last run-seq) ;; Or use an average over time
;;           {:keys [mean-quality-accepted mean-quality-rejected]}
;;           (acceptance-quality-stats final-ws)]
;;       {:noise noise-level
;;        :accepted-quality mean-quality-accepted
;;        :rejected-quality mean-quality-rejected
;;        :accept-reject-quality (- mean-quality-accepted mean-quality-rejected)})))

;; (def noise-quality-results
;;   (vec (map #(run-noise-experiment % 200) noise-levels)))

;; (def quality-vs-noise-plot
;;   {:data {:values noise-quality-results}
;;    :layer [{:mark {:type "line" :color "steelblue"}
;;             :encoding {:x {:field "noise" :type "quantitative"}
;;                        :y {:field "accepted-quality" :type "quantitative"}
;;                        :tooltip [{:field "noise"} {:field "accepted-quality"}]}}

;;            {:mark {:type "line" :color "firebrick"}
;;             :encoding {:x {:field "noise" :type "quantitative"}
;;                        :y {:field "rejected-quality" :type "quantitative"}
;;                        :tooltip [{:field "noise"} {:field "rejected-quality"}]}}

;;            {:mark {:type "line" :color "#b8860b"}
;;             :encoding {:x {:field "noise" :type "quantitative"}
;;                        :y {:field "accept-reject-quality" :type "quantitative"}
;;                        :tooltip [{:field "noise"} {:field "accept-reject-quality"}]}}]})

;; (oz/view! quality-vs-noise-plot)
