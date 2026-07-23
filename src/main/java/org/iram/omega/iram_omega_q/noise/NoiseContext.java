/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.noise;

/**
 *
 * @author veronique
 */
/**
 * NoiseContext
 *
 * Immutable context object passed to a NoiseModel when the simulation asks for
 * the disturbance amplitude at the current step.
 *
 * The purpose of this class is to give different noise models access to the
 * quantities they may need without coupling those models directly to
 * QuantumRegulationAgent, MuController, or the simulation runner.
 *
 * Examples:
 *
 *   ExternalNoiseModel may use baseExternalNoise.
 *   InducedNoiseModel may use mu and previousMu.
 *   Time-dependent noise models may use step or time.
 *
 * This class does not modify the agent. It only carries information.
 */
public final class NoiseContext {

    /**
     * Integer simulation step index.
     *
     * Useful for time-dependent protocols, scheduled perturbations, or logging.
     */
    public final int step;

    /**
     * Continuous simulation time.
     *
     * Usually this can be computed as:
     *
     *     time = step * dt
     *
     * It is included separately so that future noise models do not need to know
     * how the simulation runner defines the time step.
     */
    public final double time;

    /**
     * Current regulation level mu(t).
     *
     * This is the controller value available to the noise model at the time
     * the context is constructed.
     */
    public final double mu;

    /**
     * Previous regulation level mu(t - 1).
     *
     * This is used to compute changes in regulation, especially for induced
     * noise caused by abrupt regulatory tightening.
     */
    public final double previousMu;

    /**
     * Baseline external disturbance amplitude.
     *
     * This preserves the original IRAM-Ω-Q interpretation of an externally
     * supplied environmental or task disturbance.
     */
    public final double baseExternalNoise;

    /**
     * Construct a noise context for the current simulation step.
     *
     * @param step integer simulation step index
     * @param time continuous simulation time
     * @param mu current regulation level mu(t)
     * @param previousMu previous regulation level mu(t - 1)
     * @param baseExternalNoise baseline external disturbance amplitude
     */
    public NoiseContext(
            int step,
            double time,
            double mu,
            double previousMu,
            double baseExternalNoise
    ) {
        if (step < 0) {
            throw new IllegalArgumentException("step must be non-negative");
        }
        if (!Double.isFinite(time) || time < 0.0) {
            throw new IllegalArgumentException(
                    "time must be finite and non-negative");
        }
        if (!Double.isFinite(mu)) {
            throw new IllegalArgumentException("mu must be finite");
        }
        if (!Double.isFinite(previousMu)) {
            throw new IllegalArgumentException("previousMu must be finite");
        }
        if (!Double.isFinite(baseExternalNoise) || baseExternalNoise < 0.0) {
            throw new IllegalArgumentException(
                    "baseExternalNoise must be finite and non-negative");
        }

        this.step = step;
        this.time = time;
        this.mu = mu;
        this.previousMu = previousMu;
        this.baseExternalNoise = baseExternalNoise;
    }

    /**
     * Return the signed change in regulation:
     *
     *     deltaMu(t) = mu(t) - mu(t - 1)
     *
     * Positive values mean regulation increased.
     * Negative values mean regulation decreased.
     *
     * @return signed change in mu
     */
    public double deltaMu() {
        return mu - previousMu;
    }

    /**
     * Return the positive part of the regulation change:
     *
     *     deltaMuPlus(t) = max(0, mu(t) - mu(t - 1))
     *
     * This is used by induced-noise models where only abrupt tightening of
     * regulation creates additional disturbance.
     *
     * @return positive change in mu, or zero if mu did not increase
     */
    public double positiveDeltaMu() {
        return Math.max(0.0, deltaMu());
    }
}
