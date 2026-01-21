# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Clojure simulation studying peer review as a noisy signal extraction problem. The project models how low inter-rater reliability (ICC = 0.34) combined with multi-dimensional conjunctive evaluation creates systematic dysfunction in academic publishing. Target outlet: The Accounting Review (TAR).

## Current Status

**Paper is near submission-ready.** Key findings:
- ~45% of deserving papers rejected due to measurement error alone (baseline: 2 reviewers, ICC=0.34, threshold=20th percentile)
- Plausible bounds: 28-55% FNR depending on assumptions
- Single-reviewer journals (JAR/JAE model): ~52% FNR
- "Convergence to mediocrity": noise compresses published quality from both ends
- Volume strategy dominates quality strategy for researchers

**Remaining submission tasks:**
- Create separate Word title page (TAR requirement)
- Verify page count ≤55 pages
- Optional: Customize heading formatting for TAR style
- Proofread

## Build & Development Commands

```bash
# Run tests
lein test

# Start REPL (primary development method)
lein repl

# Compile
lein compile

# Build standalone JAR
lein uberjar

# Clean build artifacts
lein clean

# Build paper PDF (from paper/ directory)
cd paper && latexmk -pdf paper.tex
```

## Architecture

### Paper Files

**`paper/paper.org`** - Main manuscript (org-mode, exports to LaTeX)
- Abstract (135 words)
- AI disclosure statement (required by AAA)
- Keywords: peer review, publication process, inter-rater reliability, false negative rate, simulation, accounting journals
- JEL Codes: M41 (Accounting), C63 (Simulation Modeling)

### Core Modules

**`src/publication_sim/review_model.clj`** - Main peer review model (for TAR paper)
- Clean separation of reviewer and editor roles:
  - **Reviewers**: Provide noisy quality assessments that are averaged
  - **Editor**: Makes accept/reject decision based on aggregated signal (does not contribute to average)
- Papers evaluated on multiple dimensions with AND-gating (all dimensions must pass threshold)
- Noise calibrated to empirical inter-rater reliability (ICC = 0.34, noise-sd = 30)
- Key experiments:
  - `experiment-reviewer-count`: Effect of 1-10 reviewers on error rates
  - `experiment-quality-strategy`: Acceptance probability by quality level
  - `experiment-selectivity`: FNR across different acceptance rates
  - `experiment-threshold-crossing`: Shows threshold as crossover point
  - `experiment-dimension-correlation`: Effect of correlated dimensions

**`src/publication_sim/paper_experiments.clj`** - Run all experiments for paper
- Entry point: `lein run -m publication-sim.paper-experiments`
- Runs all experiments with 20 iterations for stability
- Outputs formatted tables for paper

**`src/publication_sim/core.clj`** - Original dynamic simulation (exploratory)
- Agent-based model with researchers, journals, papers over time
- Researchers learn journal thresholds and optimize submission strategy

**`src/publication_sim/multidim.clj`** - Earlier multi-dimensional analysis (exploratory)
- Predecessor to review_model.clj with similar experiments

### Key Parameters (Calibrated to Bornmann et al. 2010)

| Parameter | Value | Source |
|-----------|-------|--------|
| ICC | 0.34 | Meta-analysis of 70 studies |
| noise-sd | 30 | Derived from ICC, quality-sd=50 |
| quality-sd | 50 | Assumed true quality distribution |
| threshold | 20 | 80th percentile selectivity |
| dimensions | 2 | Interest + Rigor (conservative) |
| reviewers | 2 | Typical for accounting journals |

### Key Dependencies

- **fastmath**: Statistical functions, random distributions, correlation calculations
- **oz**: Vega-Lite visualizations (used in commented REPL experiments)

### Development Style

This is a research project targeting The Accounting Review. The `review_model.clj` module is the main codebase for the paper. Development happens interactively in the REPL.

## Key Conceptual Points

1. **Independent errors are the realistic baseline** - Long review times, multiple rounds, and contradictory demands suggest reviewer *disagreement*, not agreement. Management Science's two-round limit exists because reviewers don't agree.

2. **Threshold as crossover point** - Papers below threshold are *helped* by noise (lucky draws get them accepted), while papers above threshold are *hurt* (unlucky draws get them rejected).

3. **AND-gate amplification** - Multi-dimensional conjunctive evaluation multiplies error probabilities. With 2 dimensions at 80% pass rate each, overall pass rate drops to 64%.

4. **Mechanism operates independently of intent** - These effects arise from measurement noise alone, without requiring reviewer bias or strategic behavior.
