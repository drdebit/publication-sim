# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Clojure simulation for studying publication and peer-review systems. The project models researchers submitting papers to journals, journal acceptance/rejection decisions, and analyzes how different review mechanisms affect outcomes like quality-acceptance correlation and error rates.

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
```

## Architecture

### Core Modules

**`src/publication_sim/review_model.clj`** - Main peer review model (for TAR paper)
- Clean separation of reviewer and editor roles:
  - **Reviewers**: Provide noisy quality assessments that are averaged
  - **Editor**: Makes accept/reject decision based on aggregated signal (does not contribute to average)
- Papers evaluated on multiple dimensions with AND-gating (all dimensions must pass threshold)
- Noise calibrated to empirical inter-rater reliability (SD=30 from Bornmann)
- Key experiments:
  - `experiment-reviewer-count`: Effect of 1-10 reviewers on error rates
  - `experiment-quality-strategy`: Acceptance probability by quality level
  - `experiment-editor-as-reviewer`: Supplemental analysis showing editor signal dilutes accuracy

**`src/publication_sim/paper_experiments.clj`** - Run all experiments for paper
- Entry point: `lein run -m publication-sim.paper-experiments`
- Runs all experiments with 20 iterations for stability
- Outputs formatted tables for paper

**`src/publication_sim/core.clj`** - Original dynamic simulation (exploratory)
- Agent-based model with researchers, journals, papers over time
- Researchers learn journal thresholds and optimize submission strategy

**`src/publication_sim/multidim.clj`** - Earlier multi-dimensional analysis (exploratory)
- Predecessor to review_model.clj with similar experiments

### Key Dependencies

- **fastmath**: Statistical functions, random distributions, correlation calculations
- **oz**: Vega-Lite visualizations (used in commented REPL experiments)

### Development Style

This is a research project targeting The Accounting Review. The `review_model.clj` module is the main codebase for the paper. Development happens interactively in the REPL.
