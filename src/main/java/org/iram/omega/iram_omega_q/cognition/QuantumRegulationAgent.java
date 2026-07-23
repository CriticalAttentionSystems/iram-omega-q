/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.cognition;

import java.util.Random;
import org.iram.omega.iram_omega_q.cognition.quantum.CognitiveStateMetrics;
import org.iram.omega.iram_omega_q.control.MuController;
import org.iram.omega.iram_omega_q.noise.ExternalNoiseModel;
import org.iram.omega.iram_omega_q.noise.NoiseContext;
import org.iram.omega.iram_omega_q.noise.NoiseModel;
/**
 *
 * @author veronique
 */
/**
 * QuantumRegulationAgent
 *
 * Implements a quantum-inspired cognitive agent with adaptive mindfulness
 * regulation and exact coherent evolution.
 *
 * The control-ordering experiment is causal rather than a mere permutation
 * of two same-cycle operators:
 *
 * REGULATION_FIRST (RF):
 *     observe pre-disturbance state -> update mu -> stabilize ->
 *     suppress current-cycle disturbance -> unitary evolution
 *
 * DISTURBANCE_FIRST (DF):
 *     current-cycle disturbance enters before a new response can protect it ->
 *     observe disturbed state -> update mu -> stabilize reactively ->
 *     unitary evolution
 *
 * Thus RF represents anticipatory protection, while DF represents reactive
 * recovery after exposure.  In both cases, coherent internal evolution is
 * identical:
 *
 *     psi(t + dt) = exp(-i H dt) psi(t).
 */
public class QuantumRegulationAgent {

    /** Pure quantum cognitive state |ψ⟩ */
    private QuantumCognitiveState psi;

    /** Attention / association Hamiltonian */
    private final Hamiltonian hamiltonian;

    /** Time step for exact unitary evolution */
    private double dt = 0.01;

    /**
     * Cached exact coherent propagator U = exp(-i H dt).
     *
     * It is built once for the current Hamiltonian and time step rather than
     * diagonalizing H at every cognitive step.
     */
    private QuantumEvolution.Propagator unitaryPropagator;

    /** Baseline emotional noise (pre-mindfulness) */
    private double baseEmotionalNoise = 0.0;

    /** noise **/
    /** 
    * Noise model used to generate the raw disturbance amplitude.
    *
    * Default is external-only noise, which preserves the current Paper 1--3
    * behavior when setEmotionalNoise(noise) is used.
    */
    private NoiseModel noiseModel = new ExternalNoiseModel();

   /** Previous regulation level, used by induced-noise models. */
    private double previousMu = 0.0;

    /** Optional attentional focus index for Zeno stabilization */
    private Integer mindfulnessFocus = null;

    /** Control ordering (experimental condition) */
    private ControlOrdering ordering = ControlOrdering.REGULATION_FIRST;

    /** Entropy control */
    private final MuController muController;

    /** Random source */
    private final Random rng;

    /** Last raw disturbance sampled from the noise model before RF suppression. */
    private double lastRawNoise = 0.0;

    /** Last disturbance magnitude actually applied to the state after control-ordering
    * effects such as RF suppression.*/
    private double lastEffectiveNoise = 0.0;

    /** Delta mu visible to the noise model when disturbance is sampled. */
    private double lastDeltaMuPreNoise = 0.0;

    /** Delta mu after the controller has updated during this step. */
    private double lastDeltaMuPostUpdate = 0.0;
    /**
     * Defines whether regulation can protect against the current-cycle
     * disturbance or can only respond after that disturbance has entered.
     */
    public enum ControlOrdering {
        /**
         * Anticipatory control: update regulation and stabilize before the
         * incoming disturbance is applied.
         */
        REGULATION_FIRST,
        /**
         * Reactive control: apply the current-cycle disturbance first, then
         * update regulation from the disturbed state and stabilize afterward.
         */
        DISTURBANCE_FIRST
    }

    /** Noise model reset */
    public void resetNoiseModel(long seed) {
        noiseModel.reset(seed);
        previousMu = muController.getMu();
        lastRawNoise = 0.0;
        lastEffectiveNoise = 0.0;
        lastDeltaMuPreNoise = 0.0;
        lastDeltaMuPostUpdate = 0.0;
    }
    
    /**
     * Construct a quantum Regulation agent.
     *
     * The default experimental ordering is REGULATION_FIRST, in which
     * anticipatory regulation can reduce the current-cycle disturbance.
     */
    public QuantumRegulationAgent(
            QuantumCognitiveState psi,
            Hamiltonian hamiltonian,
            MuController muController,
            Random rng
    ) {
        if (psi == null) {
            throw new IllegalArgumentException("psi must not be null");
        }
        if (hamiltonian == null) {
            throw new IllegalArgumentException("hamiltonian must not be null");
        }
        if (muController == null) {
            throw new IllegalArgumentException("muController must not be null");
        }
        if (rng == null) {
            throw new IllegalArgumentException("rng must not be null");
        }

        this.psi = psi;
        this.hamiltonian = hamiltonian;
        this.muController = muController;
        this.rng = rng;
        this.unitaryPropagator =
                new QuantumEvolution.Propagator(hamiltonian, psi.dim(), dt);
    }

    public Random rng() {
        return rng;
    }
    
    /** Set baseline emotional noise level. */
    public void setEmotionalNoise(double noise) {
        if (!Double.isFinite(noise) || noise < 0.0) {
            throw new IllegalArgumentException(
                    "noise must be finite and non-negative");
        }

        this.baseEmotionalNoise = noise;
    }
    
    public double getEmotionalNoise() {
        return baseEmotionalNoise;
    }

    /**
    * Set a custom noise model for future experiments.
    *
    * This allows external, self-generated, induced, or composite noise without
    * changing the RF/DF control-ordering logic.
    */
    public void setNoiseModel(NoiseModel noiseModel) {
        if (noiseModel == null) {
           throw new IllegalArgumentException("noiseModel must not be null");
        }
        this.noiseModel = noiseModel;
    }
    
    public double getLastRawNoise() {
        return lastRawNoise;
    }


    public double getLastEffectiveNoise() {
        return lastEffectiveNoise;
    }
    
    
    public double getPreviousMu() {
        return previousMu;
    }
    
    public double getLastDeltaMuPreNoise() {
        return lastDeltaMuPreNoise;
    }

    public double getLastDeltaMuPostUpdate() {
        return lastDeltaMuPostUpdate;
    }
    
   
    /**
    * Sample the external and autonomous internal components of disturbance.
    *
    * This is the component RF can suppress when regulation has already been
    * updated. DF receives it before its reactive controller update.
    */
    private double sampleNonInducedNoise() {
        double currentMu = muController.getMu();

        // Preserve the existing diagnostic meaning.
        lastDeltaMuPreNoise = currentMu - previousMu;

        NoiseContext ctx = new NoiseContext(
               0,              // step index can be supplied later by simulation driver
               0.0,            // time can be supplied later by simulation driver
               currentMu,
               previousMu,
               baseEmotionalNoise
        );

        return NoiseModel.requireValidEta(
               noiseModel.nonInducedNoiseAt(ctx));
    }

   /**
    * Immediate Option B disturbance generated by a positive controller increase
    * during the current step.
    */
    private double sampleImmediateInducedNoise(
           double muAfterUpdate,
           double muBeforeUpdate
    ) {
        NoiseContext ctx = new NoiseContext(
               0,              // step index can be supplied later by simulation driver
               0.0,            // time can be supplied later by simulation driver
               muAfterUpdate,
               muBeforeUpdate,
               baseEmotionalNoise
        );

        return NoiseModel.requireValidEta(
               noiseModel.inducedNoiseAt(ctx));
    }
    
    /**
     * Set simulation timestep and rebuild the exact unitary propagator
     * U = exp(-i H dt).
     */
    public void setDt(double dt) {
        if (!Double.isFinite(dt) || dt <= 0.0) {
            throw new IllegalArgumentException("dt must be finite and positive");
        }
        this.dt = dt;
        this.unitaryPropagator =
                new QuantumEvolution.Propagator(hamiltonian, psi.dim(), dt);
    }

    public double getDt() {
        return dt;
    }

    /** Set attentional focus for Zeno-style stabilization. */
    public void setMindfulnessFocus(Integer focusIndex) {
        if (focusIndex != null &&
                (focusIndex < 0 || focusIndex >= psi.dim())) {
            throw new IllegalArgumentException(
                    "focusIndex must be in [0, " + (psi.dim() - 1) + "]");
        }
        this.mindfulnessFocus = focusIndex;
    }

    /** Disable mindfulness focus (but not adaptive μ) */
    public void clearMindfulness() {
        this.mindfulnessFocus = null;
    }

    /**
     * Perform one cognitive time step.
     *
     * REGULATION_FIRST (RF) represents anticipatory protection: the controller
     * observes the state before exposure, updates mu, stabilizes the state,
     * and uses the new regulation level to reduce disturbance entering during
     * the current cycle.
     *
     * DISTURBANCE_FIRST (DF) represents reactive recovery: the current-cycle
     * disturbance reaches the state before a new regulatory response can be
     * computed.  The controller then observes the disturbed state, updates mu,
     * and stabilizes afterward.  The resulting larger same-cycle exposure in
     * DF is the modeled consequence of delayed regulation, not an accidental
     * mismatch of experimental parameters.
     *
     * Coherent evolution is exact and identical in both branches:
     *
     *     psi(t + dt) = exp(-i H dt) psi(t)
     */
    public void step() {
        /*
         * Directly record the controller value at the start of the present cycle.
         * This is the reference for the same-step induced-noise calculation.
         */
        double muAtStepStart = muController.getMu();

        if (ordering == ControlOrdering.REGULATION_FIRST) {

            // ------------------------------------------------------------
            // REGULATION_FIRST = anticipatory protection
            // ------------------------------------------------------------

            // Observe the state before the new disturbance arrives.
            double preDisturbanceEntropy =
                    CognitiveStateMetrics.vonNeumannEntropy(psi);

            // Compute regulation early enough to act in the current cycle.
            double preventiveMu =
                    muController.update(preDisturbanceEntropy);

            // Stabilize before exposure.
            if (mindfulnessFocus != null && preventiveMu > 0.0) {
                MindfulnessOperator.stabilize(
                        psi, mindfulnessFocus, preventiveMu);
            }

            /*
             * External/self-generated noise arrives after preventive regulation
             * and can therefore be suppressed.
             */
            double nonInducedRaw = sampleNonInducedNoise();

            double nonInducedEffective =
                    nonInducedRaw * Math.max(0.0, 1.0 - preventiveMu);

            if (nonInducedEffective > 0.0) {
                DecoherenceModel.apply(
                        psi, nonInducedEffective, rng);
            }

            /*
             * Option B:
             * abrupt preventive tightening produces an immediate same-cycle cost.
             *
             * This induced component is not suppressed again by the same
             * regulation increase that generated it.
             */
            double induced = sampleImmediateInducedNoise(
                    preventiveMu,
                    muAtStepStart);

            if (induced > 0.0) {
                DecoherenceModel.apply(psi, induced, rng);
            }

            /*
             * Preserve the existing output fields. Raw includes both source
             * components before RF suppression; effective is what reached psi.
             */
            lastRawNoise = nonInducedRaw + induced;
            lastEffectiveNoise = nonInducedEffective + induced;

        } else {

            // ------------------------------------------------------------
            // DISTURBANCE_FIRST = reactive recovery
            // ------------------------------------------------------------

            /*
             * External/self-generated noise enters before a new response can
             * protect against it.
             */
            double nonInducedRaw = sampleNonInducedNoise();

            if (nonInducedRaw > 0.0) {
                DecoherenceModel.apply(
                        psi, nonInducedRaw, rng);
            }

            // Observe the already disturbed state and respond to it.
            double postDisturbanceEntropy =
                    CognitiveStateMetrics.vonNeumannEntropy(psi);

            double reactiveMu =
                    muController.update(postDisturbanceEntropy);

            // Stabilization supports recovery after exposure.
            if (mindfulnessFocus != null && reactiveMu > 0.0) {
                MindfulnessOperator.stabilize(
                        psi, mindfulnessFocus, reactiveMu);
            }

            /*
             * Reactive tightening produces immediate control-generated
             * disturbance within the same cycle.
             */
            double induced = sampleImmediateInducedNoise(
                    reactiveMu,
                    muAtStepStart);

            if (induced > 0.0) {
                DecoherenceModel.apply(psi, induced, rng);
            }

            lastRawNoise = nonInducedRaw + induced;
            lastEffectiveNoise = nonInducedRaw + induced;
        }

        // Exact coherent internal evolution, common to RF and DF.
        unitaryPropagator.apply(psi);

        // Keep your existing diagnostics exactly intact.
        double muAfterUpdate = muController.getMu();

        // This is the full same-step controller change, including reactive DF update.
        lastDeltaMuPostUpdate = muAfterUpdate - muAtStepStart;

        // Store final mu for induced-noise models in the next cycle.
        previousMu = muAfterUpdate;
    }
    
    /** Perform a cognitive "measurement" (decision / report) */
    public int measure() {
        return Measurement.sample(psi, rng);
    }

    /** Access the underlying quantum cognitive state */
    public QuantumCognitiveState state() {
        return psi;
    }

    /** Observe current mindfulness level μ */
    public double mindfulnessLevel() {
        return muController.getMu();
    }
    
    /** Set controller ordering */
    public void setControlOrdering(ControlOrdering ordering) {
        if (ordering != null) {
            this.ordering = ordering;
        }
    }

    public ControlOrdering getControlOrdering() {
        return ordering;
    }

    /**
     * Reset state. If a reset supplies a state with a different dimension,
     * rebuild the unitary propagator for that basis size.
     */
    public void resetState(QuantumCognitiveState newState) {
        if (newState == null) {
            throw new IllegalArgumentException("newState must not be null");
        }
        if (mindfulnessFocus != null && mindfulnessFocus >= newState.dim()) {
            throw new IllegalArgumentException(
                    "newState dimension is incompatible with current focusIndex");
        }
        if (newState.dim() != psi.dim()) {
            this.unitaryPropagator =
                    new QuantumEvolution.Propagator(hamiltonian, newState.dim(), dt);
        }
        this.psi = newState;
    }

    /** Controller passthrough methods */
    public void setTargetEntropy(double target) {
        muController.setTargetEntropy(target);
    }

    public double getTargetEntropy() {
        return muController.getTargetEntropy();
    }

    public void setMuLearningScale(double s) {
        muController.setLearningScale(s);
    }

    public double getMuLearningScale() {
        return muController.getLearningScale();
    }

    public void resetController(double muInit) {
        muController.reset(muInit);
        previousMu = muController.getMu();
    }

    public void resetController(double muInit, boolean clearEntropyHistory) {
        muController.reset(muInit, clearEntropyHistory);
        previousMu = muController.getMu();
    }
}