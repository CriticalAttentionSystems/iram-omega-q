# IRAM-Ω-Q paper analysis runs

This directory contains reproducible batch configurations and plotting scripts for the current model in which:

- coherent evolution is exact: `psi(t + dt) = exp(-i H dt) psi(t)`;
- `REGULATION_FIRST` and `DISTURBANCE_FIRST` use the same pre-operation controller update and the same μ-modulated disturbance;
- their only manipulation is the order of stabilization and disturbance;
- a single agent may also **alternate** between the two orderings on a periodic or Markov schedule (Paper 3).

Scripts are organized per paper under `analysis/scripts/paper1/`, `analysis/scripts/paper2/`, and `analysis/scripts/paper3/`. Run every command from the Maven/NetBeans project root.

## Corrections applied before generating new results

1. **Publication susceptibility**

   The phase sweep now computes the Paper 1 quantity

   `chi(mu, eta) = mean_runs( Var_t[Delta C(t)] after burn-in )`.

   The previous implementation measured variance across replicate time means, which is a seed-variability measure rather than temporal susceptibility.

2. **Matched dynamics between single runs and sweeps**

   `focusIndex` is now explicit in `SimulationParameters` and `RunConfig`. Both full trajectories and phase sweeps use the same configured focus index. The earlier code used index `6` for full runs but `0` for sweeps.

3. **Continuous hysteresis for Paper 2**

   `HYSTERESIS` now runs one continuous triangular target-entropy protocol. The down branch inherits the state and controller at the turn point. The earlier up and down branches were independently initialized and therefore were not a genuine carryover/hysteresis test.

4. **Controller configuration consistency**

   The command-line `RunConfig` default gains now match GUI/`SimulationParameters` defaults:

   - `muDerivativeGain = 5e-4` (`alpha0`)
   - `muTargetGain = 2e-4` (`beta0`)

5. **Dwell times**

   Dwell duration includes the final occupied sample interval. The old calculation underestimated each multi-sample dwell by one sampling interval.

6. **Explicit perturbation protocol**

   `PERTURBATION` runs in `PUBLICATION` mode so that only the configured intervention window is applied. It no longer includes exploratory periodic noise bursts.

## Run Paper 1

```bash
bash analysis/scripts/paper1/run_paper1.sh quick
```

This runs averaged RF/DF trajectories and reduced phase sweeps for checking the pipeline.

For publication-resolution sweeps:

```bash
bash analysis/scripts/paper1/run_paper1.sh publication
```

Outputs are written beneath:

```text
analysis/results/paper1/
```

Paper 1 configurations:

- `paper1_rf_averaged.properties`
- `paper1_df_averaged.properties`
- `paper1_rf_phase.properties`
- `paper1_df_phase.properties`
- quick phase-map variants for validation

Primary CSV outputs:

- `mu_ci.csv`, `coherence_ci.csv`: averaged trajectories and run-to-run standard deviation
- `meanCoherence.csv`: phase-map mean coherence gap
- `susceptibility.csv`: `mean_runs(Var_t[Delta C])`
- `muCritical.csv`: critical curve obtained from the susceptibility maximum

## Run Paper 2

```bash
bash analysis/scripts/paper2/run_paper2.sh
```

Outputs are written beneath:

```text
analysis/results/paper2/
```

Paper 2 protocols for both orderings:

- continuous target-entropy hysteresis/carryover loop;
- dwell-time/regime analysis;
- explicit disturbance pulse and recovery.

Primary CSV outputs:

- `hysteresis_loop.csv`: continuous up/down loop with branch labels
- `hysteresis_up_branch.csv`, `hysteresis_down_branch.csv`: branches extracted from the same carried trajectory
- `dwell_summary.csv`, `transition_rate.csv`
- intervention `coherence.csv`, `mu.csv`, and `settling_summary.csv`

## Run Paper 3

Paper 3 studies **intermittent** anticipatory control: a single agent alternating between RF and DF on periodic and Markov schedules, compared against an occupancy-weighted mixture of matched fixed-RF and fixed-DF references.

```bash
bash analysis/scripts/paper3/run_paper3.sh            # default
bash analysis/scripts/paper3/run_paper3.sh --smoke    # fast pipeline check
bash analysis/scripts/paper3/run_paper3.sh --plots-only
bash analysis/scripts/paper3/run_paper3.sh --full     # full high-statistics robustness run
```

Reproducibility inputs are version-controlled under:

```text
analysis/paper3/config/                       # robustness base config
analysis/paper3/config/robustness/            # phase and target manifests + sweep configs
```

Outputs are written beneath:

```text
analysis/results/paper3/
```

Key artifacts:

- `rf_df_switching_long_production_diagnostics_1000runs/`: high-statistics switching results (N = 1000 matched replicates per schedule)
- `switching_figures_1000runs/`: periodic CI95 metric plots
- robustness sweeps over the `(mu_0, eta, S*)` neighborhood, driven by the phase and target manifests

The primary measure is the nonlinear switching penalty
`C_switch = mean_mu(switching) - [p_RF * mean_mu(RF) + (1 - p_RF) * mean_mu(DF)]`,
where a negative value indicates that intermittent restoration of anticipatory control requires *less* mean regulatory gain than fixed-mode occupancy predicts.

## Interpretation boundary

The state descriptor uses a quantum-like amplitude representation and a real-valued analysis density matrix. The results support claims about adaptive regulation, ordering, hysteresis, and coherence-gap dynamics in the computational model. They do not establish physical quantum processes in the brain.
