/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.cognition;

import java.util.Random;
import org.iram.omega.iram_omega_q.cognition.quantum.CognitiveState;
import org.iram.omega.iram_omega_q.cognition.quantum.CognitiveStateMetrics;
import org.iram.omega.iram_omega_q.control.MuController;

/**
 *
 * @author veronique
 */
/**
 * QuantumConsciousAgent
 *
 * Implements a quantum-inspired cognitive agent with adaptive regulation
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
public class QuantumConsciousAgent {

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

    /** Baseline emotional noise (pre-regulation) */
    private double baseEmotionalNoise = 0.0;

    /** Optional attentional focus index for Zeno stabilization */
    private Integer regulationFocus = null;

    /** Control ordering (experimental condition) */
    private ControlOrdering ordering = ControlOrdering.REGULATION_FIRST;

    /** Entropy control */
    private final MuController muController;

    /** Random source */
    private final Random rng;

    /** Last disturbance magnitude actually applied during the current cycle. */
    private double lastEffectiveNoise = 0.0;

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

    /**
     * Construct a quantum conscious agent.
     *
     * The default experimental ordering is REGULATION_FIRST, in which
     * anticipatory regulation can reduce the current-cycle disturbance.
     */
    public QuantumConsciousAgent(
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
    public void setRegulationFocus(Integer focusIndex) {
        if (focusIndex != null &&
                (focusIndex < 0 || focusIndex >= psi.dim())) {
            throw new IllegalArgumentException(
                    "focusIndex must be in [0, " + (psi.dim() - 1) + "]");
        }
        this.regulationFocus = focusIndex;
    }

    /** Disable regulation focus (but not adaptive μ) */
    public void clearRegulation() {
        this.regulationFocus = null;
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
            if (regulationFocus != null && preventiveMu > 0.0) {
                RegulationOperator.stabilize(
                        psi, regulationFocus, preventiveMu);
            }

            // Anticipatory regulation suppresses incoming disturbance.
            double effectiveNoise =
                    baseEmotionalNoise * (1.0 - preventiveMu);
            lastEffectiveNoise = effectiveNoise;

            if (effectiveNoise > 0.0) {
                DecoherenceModel.apply(
                        psi, effectiveNoise, rng);
            }

        } else {

            // ------------------------------------------------------------
            // DISTURBANCE_FIRST = reactive recovery
            // ------------------------------------------------------------

            // The current-cycle disturbance enters before new protection can
            // be computed; reactive control cannot prevent this exposure.
            double effectiveNoise = baseEmotionalNoise;
            lastEffectiveNoise = effectiveNoise;

            if (effectiveNoise > 0.0) {
                DecoherenceModel.apply(
                        psi, effectiveNoise, rng);
            }

            // Observe the already disturbed state and respond to it.
            double postDisturbanceEntropy =
                    CognitiveStateMetrics.vonNeumannEntropy(psi);

            double reactiveMu =
                    muController.update(postDisturbanceEntropy);

            // Stabilization supports recovery after exposure.
            if (regulationFocus != null && reactiveMu > 0.0) {
                RegulationOperator.stabilize(
                        psi, regulationFocus, reactiveMu);
            }
        }

        // Exact coherent internal evolution, common to RF and DF.
        unitaryPropagator.apply(psi);
    }
    
    /** Perform a cognitive "measurement" (decision / report) */
    public int measure() {
        return Measurement.sample(psi, rng);
    }

    /** Access the underlying quantum cognitive state */
    public QuantumCognitiveState state() {
        return psi;
    }

    /** Observe current regulation level μ */
    public double regulationLevel() {
        return muController.getMu();
    }

    public double getLastEffectiveNoise() {
        return lastEffectiveNoise;
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
        if (regulationFocus != null && regulationFocus >= newState.dim()) {
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
    }

    public void resetController(double muInit, boolean clearEntropyHistory) {
        muController.reset(muInit, clearEntropyHistory);
    }
}