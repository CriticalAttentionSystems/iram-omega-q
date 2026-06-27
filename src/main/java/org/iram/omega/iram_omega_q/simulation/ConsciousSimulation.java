/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;
import org.iram.omega.iram_omega_q.cognition.Hamiltonian;
import org.iram.omega.iram_omega_q.cognition.QuantumCognitiveState;
import org.iram.omega.iram_omega_q.cognition.QuantumConsciousAgent;
import org.iram.omega.iram_omega_q.cognition.quantum.CognitiveStateMetrics;
import org.iram.omega.iram_omega_q.control.MuController;

/**
 * Central simulation driver for IRAM-Ω-Q.
 *
 * From the main app / GUI point of view, this class is the main computational
 * engine. The app prepares a SimulationParameters object, then calls one of:
 *
 *     ConsciousSimulation.run(p)
 *     ConsciousSimulation.run(p, mode)
 *     ConsciousSimulation.runAveraged(p, runs, burnInSamples)
 *     ConsciousSimulation.runSweepMeanCoherence(p, burnInSteps)
 *
 * The workflow is:
 *
 * 1. The main app creates or loads SimulationParameters.
 * 2. This class builds the initial quantum-like cognitive state.
 * 3. This class builds the Hamiltonian dynamics.
 * 4. This class builds the adaptive μ controller.
 * 5. These components are assembled into a QuantumConsciousAgent.
 * 6. The simulation loop advances the agent step by step.
 * 7. At each sampled time step, entropy, coherence gap, μ, Δμ,
 *    target entropy, effective noise, intervention flags, and reset flags
 *    are recorded.
 * 8. The result is returned to the app as a SimulationResult or AveragedResult.
 *
 * This class does not draw figures itself. It only produces the numerical
 * traces that the GUI, batch runner, CSV exporter, or plotting code can use.
 *
 * @author veronique
 */
public class ConsciousSimulation {

    /**
     * Model-side run mode.
     *
     * This is not necessarily the same as a GUI sweep mode, although the GUI
     * can map its controls onto these modes.
     *
     * EXPLORATORY:
     *     Used for fast feedback, debugging, visual testing, and stress tests.
     *     It records fewer samples using a stride and may introduce artificial
     *     noise bursts after burn-in.
     *
     * PUBLICATION:
     *     Used for reproducible paper figures and CSV output. It uses a small
     *     burn-in, records every time step, and avoids artificial perturbation
     *     bursts.
     *
     * SWEEP:
     *     Used when another routine is already controlling burn-in and
     *     aggregation. This mode does not trim samples internally.
     */
    public enum Mode {
        EXPLORATORY,
        PUBLICATION,
        SWEEP
    }

    /**
     * Convenience entry point used by the main app when no explicit mode is
     * requested.
     *
     * By default, this uses PUBLICATION mode, because that is the safest and
     * most reproducible protocol for paper figures and saved runs.
     */
    public static SimulationResult run(SimulationParameters p) {
        return run(p, Mode.PUBLICATION);
    }

    /**
     * Main single-run simulation entry point.
     *
     * This is the standard path used by the GUI for one displayed run and by
     * batch scripts for one saved trajectory.
     *
     * Inputs:
     *     p    = all model parameters, numerical controls, seeds, intervention
     *            windows, reset rules, target entropy settings, and ordering.
     *
     *     mode = execution protocol: EXPLORATORY, PUBLICATION, or SWEEP.
     *
     * Output:
     *     SimulationResult containing aligned time-series arrays.
     *
     * Important design rule:
     *     This method builds the agent, runs the loop, records metrics, and
     *     returns data. It does not mutate global state or directly plot.
     */
    public static SimulationResult run(SimulationParameters p, Mode mode) {

        /*
         * ------------------------------------------------------------------
         * 1. Create deterministic random number generator.
         * ------------------------------------------------------------------
         *
         * The seed comes from SimulationParameters. This makes a run
         * reproducible if the same parameter set and seed are used again.
         */
        final java.util.Random rng = new java.util.Random(p.seed);

        /*
         * ------------------------------------------------------------------
         * 2. Build the initial cognitive state.
         * ------------------------------------------------------------------
         *
         * buildInitialState(...) creates a quantum-like coherent state from
         * a salience profile. The salience profile is typically a Gaussian-like
         * attentional bump over a finite cognitive basis of dimension p.dim.
         *
         * psi0 is kept as a template so that reset experiments can return the
         * system to the original initial condition.
         *
         * psi is the actual mutable starting state given to the agent.
         */
        QuantumCognitiveState psi0 = buildInitialState(p, rng);
        QuantumCognitiveState psi = psi0.copy();

        /*
         * ------------------------------------------------------------------
         * 3. Build the Hamiltonian.
         * ------------------------------------------------------------------
         *
         * The Hamiltonian defines the intrinsic attentional/cognitive dynamics.
         * In this implementation, it is constructed from:
         *
         *     p.energyScale
         *     p.coupling
         *     p.locality
         *     p.dim
         *
         * The Hamiltonian is not the controller. It describes the background
         * dynamical structure of the cognitive state.
         */
        Hamiltonian H = buildHamiltonian(p);

        /*
         * ------------------------------------------------------------------
         * 4. Build the adaptive μ controller.
         * ------------------------------------------------------------------
         *
         * μ is the adaptive regulation gain. The controller tries to regulate
         * uncertainty relative to the target entropy.
         *
         * The controller is initialized with:
         *
         *     p.muInit
         *     p.muMin
         *     p.muMax
         *     p.muLearningRate
         *     p.targetEntropy
         */
        MuController muController = buildMuController(p);

        /*
         * ------------------------------------------------------------------
         * 5. Assemble the agent.
         * ------------------------------------------------------------------
         *
         * The QuantumConsciousAgent owns the evolving state, Hamiltonian,
         * controller, and random number generator.
         *
         * From this point forward, the simulation loop interacts mostly with
         * the agent.
         */
        QuantumConsciousAgent agent =
                new QuantumConsciousAgent(psi, H, muController, rng);

        /*
         * ------------------------------------------------------------------
         * 6. Transfer parameters from SimulationParameters into the agent.
         * ------------------------------------------------------------------
         *
         * dt:
         *     Time increment per simulation step.
         *
         * emotionalNoise:
         *     Baseline noise amplitude. This may later be multiplied during
         *     intervention windows.
         *
         * regulationFocus:
         *     Configured attentional focus index used by the stabilization operator.
         *
         * controlOrdering:
         *     Determines whether stabilization acts before or after the
         *     current-cycle disturbance.
         */
        agent.setDt(p.dt);
        agent.setEmotionalNoise(p.emotionalNoise);
        agent.setRegulationFocus(p.focusIndex);
        agent.setControlOrdering(p.ordering);
        
        /*
         * This is currently not used later in the method, but it documents the
         * intended idea: the original salience profile can be reconstructed and
         * reused for reset-style experiments or diagnostics.
         */
        final double[] initialSalience = buildSalienceProfile(p);
         
        /*
         * ------------------------------------------------------------------
         * 7. Select mode-specific sampling protocol.
         * ------------------------------------------------------------------
         *
         * burnIn:
         *     Number of initial integration steps not recorded. This allows
         *     transients to settle before measurements are stored.
         *
         * stride:
         *     Sampling interval. stride = 1 means record every step.
         *     stride = 10 means record every tenth step.
         *
         * noiseBursts:
         *     Whether to apply artificial short noise bursts. These are useful
         *     for exploratory stress tests but should be disabled for clean
         *     publication protocols.
         */
        final int burnIn;
        final int stride;
        final boolean noiseBursts;

        if (mode == Mode.SWEEP) {
            /*
             * Sweep mode records everything because the sweep driver or
             * aggregation function may apply its own burn-in later.
             */
            burnIn = 0;
            stride = 1;
            noiseBursts = false;

        } else if (mode == Mode.PUBLICATION) {
            /*
             * Publication mode uses a short deterministic burn-in and records
             * every sample. No artificial bursts are added.
             */
            burnIn = Math.min(200, Math.max(0, p.steps / 20));
            stride = 1;
            noiseBursts = false;

        } else {
            /*
             * Exploratory mode records fewer points for faster GUI feedback and
             * may add perturbation bursts after burn-in.
             */
            burnIn = Math.min(100, Math.max(0, p.steps / 50));
            stride = 10;
            noiseBursts = true;
        }

        /*
         * ------------------------------------------------------------------
         * 8. Prepare output container.
         * ------------------------------------------------------------------
         *
         * The result object stores all recorded time series. Every list should
         * remain aligned: index i in each list corresponds to the same sampled
         * simulation time.
         */
        SimulationResult result = new SimulationResult();

        /*
         * Save baseline noise so it can be restored at the end.
         */
        final double baseNoise = p.emotionalNoise;

        /*
         * muPrev is used to compute Δμ between recorded samples.
         */
        double muPrev = agent.regulationLevel();

        /*
         * ------------------------------------------------------------------
         * 9. Main simulation loop.
         * ------------------------------------------------------------------
         *
         * Each iteration corresponds to one integration step of the model.
         *
         * The order inside the loop is:
         *
         *     a. Determine whether an intervention is active.
         *     b. Determine the scheduled target entropy.
         *     c. Determine the μ learning scale.
         *     d. Determine the effective noise level.
         *     e. Apply any reset events.
         *     f. Step the agent once.
         *     g. Convert state to density-like CognitiveState.
         *     h. Apply burn-in and stride gates.
         *     i. Compute metrics.
         *     j. Record aligned outputs.
         */
        for (int t = 0; t < p.steps; t++) {

            /*
             * resetNow is stored in the output so later plots can mark reset
             * events.
             */
            boolean resetNow = false;

            /*
             * Intervention window flag.
             *
             * An intervention can temporarily change target entropy, noise,
             * and μ learning scale.
             */
            boolean intervention = inWindow(t, p.interventionStart, p.interventionEnd);

            /*
             * --------------------------------------------------------------
             * 9a. Scheduled target entropy.
             * --------------------------------------------------------------
             *
             * The default target is p.targetEntropy.
             *
             * If a linear schedule is enabled, the target interpolates from
             * p.targetEntropyStart to p.targetEntropyEnd over the full run.
             *
             * If an intervention target is specified, it overrides the current
             * target during the intervention window.
             */
            double scheduledTarget = scheduledTargetEntropy(p, t);

            if (intervention && p.targetEntropyDuringIntervention != null) {
                scheduledTarget = p.targetEntropyDuringIntervention;
            }

            /*
             * Send the scheduled target to the agent/controller.
             */
            agent.setTargetEntropy(scheduledTarget);

            /*
             * --------------------------------------------------------------
             * 9b. Scheduled μ learning scale.
             * --------------------------------------------------------------
             *
             * During intervention, the controller can be made more or less
             * adaptive by scaling the μ learning rate.
             */
            agent.setMuLearningScale(
                    intervention ? p.muLearningScaleDuringIntervention : 1.0
            );

            /*
             * --------------------------------------------------------------
             * 9c. Scheduled noise.
             * --------------------------------------------------------------
             *
             * The baseline emotional noise is p.emotionalNoise.
             *
             * During intervention, it is multiplied by
             * p.noiseMultiplierDuringIntervention.
             */
            double scheduledNoise =
                    baseNoise * (intervention ? p.noiseMultiplierDuringIntervention : 1.0);

            /*
             * Optional exploratory perturbation.
             *
             * This is deliberately disabled in PUBLICATION mode.
             * It creates short bursts every 400 steps, lasting 12 steps.
             */
            if (noiseBursts && t >= burnIn) {
                boolean burst = (t % 400) < 12;
                if (burst) {
                    scheduledNoise *= 6.0;
                }
            }

            /*
             * Send the effective scheduled noise to the agent.
             */
            agent.setEmotionalNoise(scheduledNoise);

            /*
             * --------------------------------------------------------------
             * 9d. Reset events.
             * --------------------------------------------------------------
             *
             * The model supports two independent reset types:
             *
             *     state reset:
             *         Replace the cognitive state.
             *
             *     μ/controller reset:
             *         Reset the adaptive gain μ and optionally clear controller
             *         history.
             *
             * These are useful for amnesia, recovery, perturbation, and
             * hysteresis-style protocols.
             */

            if (t == p.resetStateAtStep) {
                QuantumCognitiveState newState =
                        p.resetToInitialState ? psi0.copy() : buildInitialState(p, rng);

                agent.resetState(newState);
                resetNow = true;
            }

            if (t == p.resetMuAtStep) {
                agent.resetController(p.muInit, !p.keepControllerHistoryOnReset);
                resetNow = true;

                /*
                 * Reset muPrev so that the next Δμ does not contain an
                 * artificial jump caused only by the reset.
                 */
                muPrev = agent.regulationLevel();
            }

            /*
             * --------------------------------------------------------------
             * 9e. Advance the agent by one time step.
             * --------------------------------------------------------------
             *
             * This is where the actual model update happens. Internally, the
             * agent applies the configured control ordering, noise, Hamiltonian
             * dynamics, entropy regulation, and μ adaptation.
             */
            agent.step();

            /*
             * --------------------------------------------------------------
             * 9d. Burn-in and stride gates.
             * --------------------------------------------------------------
             *
             * These gates are applied before recording anything so that all
             * output lists remain aligned and contain only accepted samples.
             */
            if (t < burnIn) {
                /*
                 * Keep the previous-μ reference synchronized through burn-in so
                 * the first recorded Delta mu is not the entire discarded
                 * transient compressed into one sample.
                 */
                muPrev = agent.regulationLevel();
                continue;
            }

            if ((t % stride) != 0) {
                continue;
            }

            /*
            * --------------------------------------------------------------
            * 9e. Compute primary diagnostics.
            * --------------------------------------------------------------
            *
            * S:
            *     von Neumann entropy, computed from the amplitude-state
            *     representation using the fast rank-two spectrum.
            *
            * dC:
            *     coherence gap ΔC = Sdiag - SvN.
            *
            * These are computed once per recorded sample and reused below.
            */
            //double S = CognitiveStateMetrics.vonNeumannEntropy(agent.state());
            //double dC = CognitiveStateMetrics.coherenceGap(agent.state());
            double S = CognitiveStateMetrics.vonNeumannEntropy(agent.state());
            double dC = CognitiveStateMetrics.coherenceGap(agent.state(), S);
            /*
             * --------------------------------------------------------------
             * 9f. Record aligned time series.
             * --------------------------------------------------------------
             *
             * All result lists receive one value per accepted sample.
             */
            result.time.add(t * p.dt);
            result.entropy.add(S);
            result.coherence.add(dC);

            /*
             * Record μ and Δμ.
             *
             * μ is the current adaptive regulation level.
             * Δμ is the change since the previous recorded sample.
             */
            double muNow = agent.regulationLevel();

            result.mu.add(muNow);
            result.deltaMu.add(muNow - muPrev);

            muPrev = muNow;

            /*
             * --------------------------------------------------------------
             * 9g. Lyapunov-style diagnostic.
             * --------------------------------------------------------------
             *
             * V is not a formal proof of stability here. It is a diagnostic
             * scalar combining coherence gap and entropy tracking error:
             *
             *     V(t) = ΔC(t) + 0.5 * (S(t) - targetEntropy(t))^2
             *
             * dV is the change in V between recorded samples.
             */
            double Vt = 1.0 * dC + 0.5 * Math.pow(S - scheduledTarget, 2);

            result.V.add(Vt);

            if (result.V.size() > 1) {
                double dVt = Vt - result.V.get(result.V.size() - 2);
                result.dV.add(dVt);
            } else {
                result.dV.add(0.0);
            }

            /*
             * --------------------------------------------------------------
             * 9f. Record experimental protocol traces.
             * --------------------------------------------------------------
             *
             * These outputs make plots and CSV files self-documenting:
             *
             *     targetEntropy:
             *         The actual target used at this time step.
             *
             *     effectiveNoise:
             *         The actual noise value applied by the agent.
             *
             *     interventionFlag:
             *         1 if inside intervention window, otherwise 0.
             *
             *     resetFlag:
             *         1 if a state or controller reset happened at this step,
             *         otherwise 0.
             */
            result.targetEntropy.add(scheduledTarget);
            result.effectiveNoise.add(agent.getLastEffectiveNoise());
            result.interventionFlag.add(intervention ? 1 : 0);
            result.resetFlag.add(resetNow ? 1 : 0);
        }

        /*
         * ------------------------------------------------------------------
         * 10. Restore baseline settings.
         * ------------------------------------------------------------------
         *
         * This is defensive cleanup. In the current method the agent is local
         * and not reused outside the method, but restoring defaults avoids
         * surprises if this code is later refactored.
         */
        agent.setEmotionalNoise(baseNoise);
        agent.setMuLearningScale(1.0);
        agent.setTargetEntropy(p.targetEntropy);

        /*
         * Return the complete recorded trajectory to the GUI, batch runner,
         * CSV exporter, or plotting code.
         */
        return result;
    }

    /* ==================== HELPERS ==================== */

    /**
     * Returns true when time step t lies inside a closed intervention/reset
     * window [start, end].
     *
     * A negative start disables the window.
     */
    private static boolean inWindow(int t, int start, int end) {
        return start >= 0 && end >= start && t >= start && t <= end;
    }

    /**
     * Linear interpolation helper.
     *
     * Used for protocols where the target entropy slowly ramps from one value
     * to another over the full simulation.
     */
    private static double linearSchedule(int t, int totalSteps, double start, double end) {
        if (totalSteps <= 1) {
            return end;
        }

        double x = (double) t / (double) (totalSteps - 1);
        return start + x * (end - start);
    }

    /**
     * Return the target entropy prescribed by the configured slow protocol.
     *
     * TRIANGULAR is the publication-safe hysteresis protocol: target entropy
     * rises from start to end and then returns to start within one continuous
     * simulation.  No state or controller reset occurs at the turning point.
     */
    private static double scheduledTargetEntropy(SimulationParameters p, int t) {
        if (Double.isNaN(p.targetEntropyStart) ||
                Double.isNaN(p.targetEntropyEnd)) {
            return p.targetEntropy;
        }

        if (p.targetEntropySchedule == SimulationParameters.ScheduleType.LINEAR) {
            return linearSchedule(t, p.steps, p.targetEntropyStart, p.targetEntropyEnd);
        }

        if (p.targetEntropySchedule == SimulationParameters.ScheduleType.TRIANGULAR) {
            if (p.steps <= 1) {
                return p.targetEntropyStart;
            }

            double x = (double) t / (double) (p.steps - 1);
            if (x <= 0.5) {
                return p.targetEntropyStart
                        + (2.0 * x) * (p.targetEntropyEnd - p.targetEntropyStart);
            }

            return p.targetEntropyEnd
                    + (2.0 * x - 1.0) * (p.targetEntropyStart - p.targetEntropyEnd);
        }

        return p.targetEntropy;
    }

    /**
     * Builds the salience profile used to initialize the cognitive state.
     *
     * The profile is a Gaussian-like bump over the discrete cognitive basis.
     *
     * Parameters:
     *
     *     p.dim:
     *         Number of basis states.
     *
     *     p.salienceCenter:
     *         Center of the attentional bump.
     *
     *     p.salienceWidth:
     *         Width of the attentional bump.
     */
    private static double[] buildSalienceProfile(SimulationParameters p) {
        double[] salience = new double[p.dim];

        for (int i = 0; i < p.dim; i++) {
            salience[i] =
                    Math.exp(-0.5 * Math.pow((i - p.salienceCenter) / p.salienceWidth, 2));
        }

        return salience;
    }

    /**
     * Builds the initial quantum-like cognitive state.
     *
     * Workflow:
     *
     *     1. Construct the salience profile.
     *     2. Convert it into a coherent cognitive state.
     *     3. Add phase noise using p.phaseNoise and the seeded RNG.
     *
     * The returned state is reproducible for a fixed seed.
     */
    private static QuantumCognitiveState buildInitialState(
            SimulationParameters p,
            java.util.Random rng
    ) {
        double[] salience = buildSalienceProfile(p);
        return QuantumCognitiveState.salienceCoherent(salience, p.phaseNoise, rng);
    }

    /**
     * Builds the Hamiltonian that defines the intrinsic cognitive dynamics.
     *
     * The energies are generated deterministically from a sinusoidal profile.
     * Then Hamiltonian.attentional(...) constructs a locality-constrained
     * attentional Hamiltonian using the coupling and locality parameters.
     */
    private static Hamiltonian buildHamiltonian(SimulationParameters p) {
        double[] energies = new double[p.dim];

        for (int i = 0; i < p.dim; i++) {
            energies[i] = p.energyScale * Math.sin(i * 0.7);
        }

        return Hamiltonian.attentional(energies, p.coupling, p.locality);
    }

    /**
     * Builds the adaptive μ controller.
     *
     * μ is initialized at p.muInit and constrained to [p.muMin, p.muMax].
     * The controller adapts using p.muLearningRate and tries to regulate the
     * system relative to p.targetEntropy.
     */
    private static MuController buildMuController(SimulationParameters p) {
        return new MuController(
                p.muInit,
                p.muMin,
                p.muMax,
                p.muDerivativeGain,   // alpha0
                p.muTargetGain,       // beta0
                p.targetEntropy
        );
    }

    /* ==================== DATA TYPES ==================== */

    /**
     * Output container for one simulation run.
     *
     * All lists are aligned by index. For example:
     *
     *     time.get(i)
     *     entropy.get(i)
     *     coherence.get(i)
     *     mu.get(i)
     *     targetEntropy.get(i)
     *
     * all refer to the same recorded sample.
     */
    public static class SimulationResult {

        /*
         * Primary time-series outputs.
         */
        public List<Double> time = new ArrayList<>();
        public List<Double> entropy = new ArrayList<>();
        public List<Double> coherence = new ArrayList<>();
        public List<Double> mu = new ArrayList<>();
        public List<Double> deltaMu = new ArrayList<>();

        /*
         * Lyapunov-style diagnostics.
         *
         * V:
         *     Scalar diagnostic combining coherence gap and entropy tracking
         *     error.
         *
         * dV:
         *     Difference in V between consecutive recorded samples.
         */
        public List<Double> V = new ArrayList<>();
        public List<Double> dV = new ArrayList<>();

        /*
         * Protocol traces.
         *
         * These make the output useful for publication figures because the
         * exported CSV can show not only what the agent did, but also what
         * schedule or intervention was applied.
         */
        public List<Double> targetEntropy = new ArrayList<>();
        public List<Double> effectiveNoise = new ArrayList<>();
        public List<Integer> interventionFlag = new ArrayList<>();
        public List<Integer> resetFlag = new ArrayList<>();
    }

    /* ==================== AVERAGING ==================== */

    /**
     * Runs several independent publication-mode simulations and averages the
     * resulting time series.
     *
     * This is the main path for publication figures with error bands.
     *
     * Workflow:
     *
     *     1. Create an empty list of individual SimulationResult objects.
     *     2. For each run:
     *          a. Copy the base parameters.
     *          b. Derive a reproducible mixed seed.
     *          c. Run a full PUBLICATION simulation.
     *          d. Store the result.
     *     3. Find the minimum common trajectory length.
     *     4. Drop burnInSamples from the recorded result arrays.
     *     5. For each remaining sample index:
     *          a. Compute mean entropy and standard deviation.
     *          b. Compute mean coherence and standard deviation.
     *          c. Compute mean μ and standard deviation.
     *          d. Compute mean Δμ and standard deviation.
     *     6. Return AveragedResult.
     */
    public static AveragedResult runAveraged(
            SimulationParameters p,
            int runs,
            int burnInSamples
    ) {
        List<SimulationResult> results = new ArrayList<>();

        for (int r = 0; r < runs; r++) {
            /*
             * Copy the parameters so each run can receive its own seed without
             * modifying the caller's original SimulationParameters object.
             */
            SimulationParameters pr = p.copy();

            /*
             * Mix the base seed with the replicate index only.
             *
             * The same baseSeed therefore produces paired stochastic histories
             * for REGULATION_FIRST and DISTURBANCE_FIRST comparisons.
             */
            pr.seed = Util.mixSeed(
                    pr.baseSeed,
                    -1,
                    -1,
                    r
            );
            
            /*
             * Use publication mode so averaged traces are clean and comparable.
             */
            results.add(run(pr, Mode.PUBLICATION));
        }

        /*
         * Because all runs should be similar but may differ in edge cases,
         * average only over the common valid length.
         */
        int minLen = results.stream()
                .mapToInt(r -> Math.min(
                r.entropy.size(),
                Math.min(r.coherence.size(), r.mu.size())
        ))
                .min()
                .orElse(0);

        if (minLen == 0) {
            throw new IllegalStateException("No samples produced (minLen=0).");
        }

        if (burnInSamples >= minLen) {
            throw new IllegalArgumentException(
                    "burnInSamples must be < number of recorded samples. burnInSamples="
                    + burnInSamples + " minLen=" + minLen
            );
        }

        /*
         * Number of averaged samples after dropping recorded burn-in samples.
         */
        int steps = minLen - burnInSamples;

        /*
         * Prepare averaged output structure.
         */
        AveragedResult avg = new AveragedResult();

        avg.time = new ArrayList<>();
        avg.meanEntropy = new ArrayList<>();
        avg.stdEntropy = new ArrayList<>();
        avg.meanCoherence = new ArrayList<>();
        avg.stdCoherence = new ArrayList<>();
        avg.meanMu = new ArrayList<>();
        avg.stdMu = new ArrayList<>();
        avg.meanDeltaMu = new ArrayList<>();
        avg.stdDeltaMu = new ArrayList<>();

        /*
         * Compute sample-wise mean and standard deviation across runs.
         */
        for (int i = 0; i < steps; i++) {
            final int idx = i + burnInSamples;

            avg.time.add(results.get(0).time.get(idx));

            avg.meanEntropy.add(mean(results, r -> r.entropy.get(idx)));
            avg.stdEntropy.add(std(results, r -> r.entropy.get(idx)));

            avg.meanCoherence.add(mean(results, r -> r.coherence.get(idx)));
            avg.stdCoherence.add(std(results, r -> r.coherence.get(idx)));

            avg.meanMu.add(mean(results, r -> r.mu.get(idx)));
            avg.stdMu.add(std(results, r -> r.mu.get(idx)));

            avg.meanDeltaMu.add(mean(results, r -> r.deltaMu.get(idx)));
            avg.stdDeltaMu.add(std(results, r -> r.deltaMu.get(idx)));
        }

        return avg;
    }

    /* ==================== STATS ==================== */

    /**
     * Computes the mean of a selected quantity over several SimulationResult
     * objects.
     *
     * The function f selects the value to average from each result.
     */
    private static double mean(
            List<SimulationResult> rs,
            ToDoubleFunction<SimulationResult> f
    ) {
        return rs.stream().mapToDouble(f).average().orElse(0);
    }

    /**
     * Computes the population standard deviation of a selected quantity over
     * several SimulationResult objects.
     *
     * This is used for plotting run-to-run variability.
     */
    private static double std(
            List<SimulationResult> rs,
            ToDoubleFunction<SimulationResult> f
    ) {
        double m = mean(rs, f);

        double v = rs.stream()
                .mapToDouble(r -> Math.pow(f.applyAsDouble(r) - m, 2))
                .average()
                .orElse(0);

        return Math.sqrt(v);
    }

    /**
     * Fast sweep path.
     *
     * This method is optimized for phase diagrams and parameter sweeps.
     *
     * Instead of storing a full SimulationResult, it runs the agent and returns
     * only the mean coherence gap ΔC after burn-in.
     *
     * This is useful when the GUI or batch runner needs to evaluate many
     * combinations of:
     *
     *     μ
     *     noise
     *     ordering
     *     target entropy
     *
     * For example, a phase diagram may call this method hundreds or thousands
     * of times. Avoiding full time-series storage keeps the sweep faster and
     * lighter in memory.
     */
    /**
     * Sweep summary for a single seeded trajectory after burn-in.
     *
     * meanCoherence is the time mean of Delta C.
     * temporalVariance is Var_t[Delta C(t)] over the accepted window and is
     * the susceptibility observable used in Paper 1.
     */
    public static final class SweepStatistics {
        public final double meanCoherence;
        public final double temporalVariance;

        public SweepStatistics(double meanCoherence, double temporalVariance) {
            this.meanCoherence = meanCoherence;
            this.temporalVariance = temporalVariance;
        }
    }

    /**
     * Compatibility path returning only the post-burn-in time-mean Delta C.
     */
    public static double runSweepMeanCoherence(
            SimulationParameters p,
            int burnInSteps
    ) {
        return runSweepStatistics(p, burnInSteps).meanCoherence;
    }

    /**
     * Fast sweep path returning the two post-burn-in statistics required by
     * the publication phase diagram:
     *
     *     <Delta C>_t
     *     chi = Var_t[Delta C(t)].
     */
    public static SweepStatistics runSweepStatistics(
            SimulationParameters p,
            int burnInSteps
    ) {
        if (p == null) {
            throw new IllegalArgumentException("parameters must not be null");
        }
        if (burnInSteps < 0 || burnInSteps >= p.steps) {
            throw new IllegalArgumentException(
                    "burnInSteps must be in [0, steps), got " + burnInSteps);
        }

        final java.util.Random rng = new java.util.Random(p.seed);

        QuantumCognitiveState psi0 = buildInitialState(p, rng);
        QuantumCognitiveState psi = psi0.copy();

        Hamiltonian H = buildHamiltonian(p);
        MuController muController = buildMuController(p);

        QuantumConsciousAgent agent =
                new QuantumConsciousAgent(psi, H, muController, rng);

        agent.setDt(p.dt);
        agent.setEmotionalNoise(p.emotionalNoise);
        agent.setRegulationFocus(p.focusIndex);
        agent.setControlOrdering(p.ordering);

        double sum = 0.0;
        double sumSquares = 0.0;
        int n = 0;

        for (int t = 0; t < p.steps; t++) {
            boolean intervention = inWindow(t, p.interventionStart, p.interventionEnd);

            double scheduledTarget = scheduledTargetEntropy(p, t);
            if (intervention && p.targetEntropyDuringIntervention != null) {
                scheduledTarget = p.targetEntropyDuringIntervention;
            }

            agent.setTargetEntropy(scheduledTarget);
            agent.setMuLearningScale(
                    intervention ? p.muLearningScaleDuringIntervention : 1.0
            );
            agent.setEmotionalNoise(
                    p.emotionalNoise
                            * (intervention ? p.noiseMultiplierDuringIntervention : 1.0)
            );

            if (t == p.resetStateAtStep) {
                agent.resetState(
                        p.resetToInitialState ? psi0.copy() : buildInitialState(p, rng)
                );
            }

            if (t == p.resetMuAtStep) {
                agent.resetController(p.muInit, !p.keepControllerHistoryOnReset);
            }

            agent.step();

            if (t < burnInSteps) {
                continue;
            }

            double dC = CognitiveStateMetrics.coherenceGap(agent.state());
            sum += dC;
            sumSquares += dC * dC;
            n++;
        }

        if (n == 0) {
            return new SweepStatistics(Double.NaN, Double.NaN);
        }

        double mean = sum / n;
        double variance = Math.max(0.0, sumSquares / n - mean * mean);
        return new SweepStatistics(mean, variance);
    }
}