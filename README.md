# IRAM-Ω-Q

**IRAM-Ω-Q** is a modular, Java-based research framework for modeling **adaptive uncertainty regulation** in artificial agents.

The system implements an **integrated regulatory architecture** in which regulation, control, and stability are treated as **measurable system-level properties**, rather than symbolic labels or subjective claims.

IRAM-Ω-Q does **not** assert phenomenological consciousness. Instead, it operationalizes multiple criteria discussed in contemporary **cognitive science**, **control theory**, and **artificial life**, including:

- Global state integration  
- Adaptive self-regulation under noise  
- Identity persistence across perturbations  
- Self-monitoring of internal metrics  
- Order-dependent perception–action coupling  

---

## Core Architecture

At its core, IRAM-Ω-Q models cognition as an **open dynamical system with feedback**.

### Key Features

#### Quantum-inspired state representations
Cognitive states are represented using **density matrices**, enabling direct computation of entropy, purity, and coherence without claiming physical quantum implementation.

#### Adaptive control parameter (μ)
A mindfulness-like regulatory signal dynamically modulates the perception–action balance in response to internal uncertainty and external noise.

#### Order-dependent dynamics
The framework explicitly distinguishes **regulation-first (RF)** and **disturbance-first (DF)** update orderings — anticipatory versus reactive self-correction — revealing qualitatively different stability regimes under identical conditions.

#### Intermittent and source-structured regulation
Beyond fixed orderings, the framework supports **time-varying schedules** that alternate between RF and DF (periodic and Markov switching), and **structured disturbance sources** (external, internal, and control-generated noise), used to study how timing and noise origin reshape regulatory burden.

#### Global metrics (Ω)
System-level measures—such as entropy production, coherence gap, and stability—are tracked continuously, enabling detection of **phase transitions** and **control breakdowns**.

---

## Scientific Positioning

IRAM-Ω-Q is positioned between:

- Classical symbolic architectures  
- End-to-end neural systems  
- Purely reinforcement-learning agents  

Rather than optimizing task performance, it emphasizes **regulatory structure**, making it suitable for studying:

- Resilience under stochastic perturbation  
- Uncertainty regulation as a control problem  
- Self-stabilization versus runaway behavior  
- Emergent phase boundaries in adaptive systems  

---

## Intended Use

IRAM-Ω-Q is designed as:

- A **research testbed**, not a product  
- A **modeling framework**, not a consciousness claim  
- A **measurement-driven architecture**, not a metaphor  

Its primary goal is to support **reproducible experiments** exploring how integrated regulation can stabilize complex agents in noisy environments.

---

## Publications

The framework underlies a connected series of studies. Papers 1–3 are publicly available; papers 4–5 are in preparation.

1. **IRAM-Ω-Q: A Computational Framework for Uncertainty Regulation in Adaptive Agents.** arXiv:2603.16020
2. **Anticipatory Regulation, Control Demand, and Hysteresis in Artificial Agents.** arXiv:2606.30975
3. **Intermittent Control Is Not Diluted Control: A Switching Effect in Artificial Agency.** arXiv:2607.17432
4. *The Source of Disturbance Matters: External, Internal, and Control-Generated Noise in Adaptive Regulation.* (in preparation)
5. *Mindfulness for AI: Pre-Action Regulation, Veto Windows, and Counterfactual Agency in Artificial Agents.* (in preparation)

Reproducible batch configurations and plotting scripts for each study are documented in [`analysis/README_PAPER_RUNS.md`](analysis/README_PAPER_RUNS.md).

---

## Status

The framework supports reproducible runs for the published studies:

- Fixed-ordering RF/DF sweeps, susceptibility, and hysteresis (Papers 1–2)  
- Periodic and Markov switching schedules with matched-replicate statistics (Paper 3)  
- Structured-noise studies over external, internal, and control-generated disturbance (Paper 4, in preparation)  
- Parameter sweeps over control and noise variables, with live visualization and exportable data for offline analysis  

Planned future work includes formal stability analysis, adaptive sweep strategies, and comparative studies with classical control architectures.
