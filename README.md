# Peer Review Simulation

A Clojure simulation studying peer review as a noisy signal extraction problem, demonstrating how low inter-rater reliability combined with multi-dimensional conjunctive evaluation creates systematic dysfunction in academic publishing.

## Key Findings

- **~45% false negative rate** at baseline (2 reviewers, ICC=0.34, 80th percentile threshold)
- **Plausible bounds: 28-55%** depending on assumptions
- **Single-reviewer journals: ~52%** (relevant for JAR/JAE model)
- **Volume beats quality**: Researchers maximize expected publications with multiple moderate-quality papers rather than single excellent papers
- **Convergence to mediocrity**: Noise compresses published quality from both ends

## Paper

The accompanying paper, "Why Good Papers Get Rejected: A Simulation of Peer Review as Noisy Signal Extraction," is being prepared for submission to *The Accounting Review*.

See `paper/paper.org` for the manuscript (org-mode format).

## Usage

```bash
# Start REPL for interactive development
lein repl

# Run all paper experiments
lein run -m publication-sim.paper-experiments

# Run tests
lein test
```

## Project Structure

- `src/publication_sim/review_model.clj` - Main peer review model
- `src/publication_sim/paper_experiments.clj` - Reproducible experiments for paper
- `paper/paper.org` - Manuscript in org-mode
- `peer_review_literature_summary.md` - Literature review notes

## Key Parameters

| Parameter | Value | Source |
|-----------|-------|--------|
| ICC | 0.34 | Bornmann et al. (2010) meta-analysis |
| noise-sd | 30 | Derived from ICC |
| threshold | 20 | 80th percentile selectivity |
| dimensions | 2 | Conservative (Interest + Rigor) |
| reviewers | 2 | Typical for accounting journals |

## Dependencies

- Clojure 1.11+
- [fastmath](https://github.com/generateme/fastmath) - Statistical functions
- [oz](https://github.com/metasoarous/oz) - Visualizations (optional)

## License

Copyright 2025-2026 Matthew D. DeAngelis

Eclipse Public License 2.0
