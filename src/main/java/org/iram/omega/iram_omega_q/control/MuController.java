/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.control;

/**
 *
 * @author veronique
 */
/**
 * 
 * Mathematically:
 * mu_{t+1}=mu_tn+ alpha_0(1-mu_t).S_t + beta_0mu_t (S_t - S^*)

  Interpretation
	•	First term: anticipatory correction if entropy is rising
	•	Second term: homeostatic pull toward target entropy
	•	Gain scheduling prevents runaway near μ → 1

  This is a nonlinear feedback controller.
 */
/**
 * Adaptive controller for the regulation gain mu.
 *
 * <p>Role in this project:</p>
 * <ul>
 *   <li>mu is the model's mindfulness-like regulation gain</li>
 *   <li>higher mu means stronger regulation / disturbance suppression</li>
 *   <li>lower mu means weaker regulation / greater exposure to disturbance</li>
 * </ul>
 *
 * <p>The controller updates mu from an entropy signal supplied by the agent.
 * In the current IRAM-Omega-Q study, that entropy signal is the
 * <b>von Neumann entropy</b> of the current cognitive state:</p>
 *
 * <pre>
 * entropy = CognitiveStateMetrics.vonNeumannEntropy(state)
 * </pre>
 *
 * <p>Important:</p>
 * <ul>
 *   <li>This controller is designed to use the full-state uncertainty signal
 *       (SvN), not the diagonal-only entropy Sdiag.</li>
 *   <li>The coherence gap ΔC is also not the direct control input. It is a
 *       diagnostic/output observable used to characterize how much organized
 *       structure survives under regulation.</li>
 * </ul>
 *
 * <p>So the intended control loop is:</p>
 *
 * <pre>
 * SvN  ->  MuController.update(...)  ->  mu
 * mu   ->  disturbance suppression / stabilization
 * new state -> new SvN
 * </pre>
 */
public class MuController {

    /** Current adaptive regulation gain. */
    private double mu;

    /** Lower and upper saturation bounds for mu. */
    private final double muMin;
    private final double muMax;

    /**
     * Base learning rates for the two controller terms.
     *
     * alpha0 weights the derivative-like term (response to entropy change).
     * beta0  weights the error-to-target term (response to entropy mismatch).
     */
    private final double alpha0;
    private final double beta0;

    /**
     * Target entropy level the controller tries to regulate toward.
     *
     * In the current study, this target is interpreted with respect to the
     * von Neumann entropy signal supplied to update(...).
     */
    private double targetEntropy;

    /**
     * Last entropy value seen by the controller.
     *
     * Used to estimate a simple finite-difference entropy derivative:
     *
     *   dSdt ~ S(t) - S(t-1)
     */
    private double lastEntropy = Double.NaN;

    /**
     * External scaling factor for the controller update.
     *
     * This allows temporary strengthening or weakening of the controller
     * without changing the base gains alpha0 and beta0.
     */
    private double learningScale = 1.0;

    /**
     * Construct a mu-controller.
     *
     * @param muInit initial regulation gain
     * @param muMin lower bound for mu
     * @param muMax upper bound for mu
     * @param alpha0 base gain for the entropy-derivative term
     * @param beta0 base gain for the entropy-error term
     * @param targetEntropy target entropy level for regulation
     */
    public MuController(
            double muInit,
            double muMin,
            double muMax,
            double alpha0,
            double beta0,
            double targetEntropy
    ) {
        this.mu = muInit;
        this.muMin = muMin;
        this.muMax = muMax;
        this.alpha0 = alpha0;
        this.beta0 = beta0;
        this.targetEntropy = targetEntropy;
    }

    /**
     * Update the regulation gain mu from the current entropy signal.
     *
     * <p><b>Which entropy is used?</b></p>
     * <p>In this project, the intended input here is the
     * <b>von Neumann entropy</b> SvN of the current cognitive state.
     * That is the controller-facing uncertainty measure.</p>
     *
     * <p>The update combines two effects:</p>
     * <ol>
     *   <li><b>Entropy-derivative response</b>:
     *       if entropy is rising quickly, mu is pushed upward more strongly.</li>
     *   <li><b>Error-to-target response</b>:
     *       if entropy is above the target, mu is increased;
     *       if entropy is below the target, pressure to increase mu is reduced.</li>
     * </ol>
     *
     * <p>The gains are also scheduled by the current mu value:</p>
     * <ul>
     *   <li>alpha decreases as mu approaches 1, reducing aggressive upward
     *       changes near the high-regulation end.</li>
     *   <li>beta increases with mu, strengthening target-tracking pressure as
     *       regulation becomes stronger.</li>
     * </ul>
     *
     * <p>Interpretation in this study:</p>
     * <ul>
     *   <li>high entropy / rising entropy -> stronger regulation pressure</li>
     *   <li>low entropy / near-target entropy -> weaker update pressure</li>
     * </ul>
     *
     * @param entropy controller input entropy signal; intended to be SvN
     * @return updated and saturated mu
     */
    public double update(double entropy) {

        // Finite-difference estimate of entropy change.
        // On the first call there is no history, so use 0.
        double dSdt = 0.0;
        if (!Double.isNaN(lastEntropy)) {
            dSdt = entropy - lastEntropy;
        }
        lastEntropy = entropy;

        // Gain scheduling:
        // - alpha gets smaller as mu approaches 1, preventing overly strong
        //   upward pushes near saturation.
        // - beta gets larger with mu, strengthening target-error correction
        //   when the controller is already active.
        double alpha = learningScale * alpha0 * (1.0 - mu);
        double beta  = learningScale * beta0  * mu;

        // Regulation update:
        //
        //    alpha * dSdt
        //       responds to rising/falling entropy
        //
        //    beta * (entropy - targetEntropy)
        //       pulls entropy toward the target
        //
        // Since entropy is intended to be SvN, this means mu is adapted
        // against the full-state uncertainty measure used in the model.
        mu += alpha * dSdt
            + beta  * (entropy - targetEntropy);

        // Saturate mu to the allowed control range.
        mu = Math.max(muMin, Math.min(muMax, mu));

        return mu;
    }

    /**
     * Reset the controller to a chosen mu value and clear entropy history.
     *
     * <p>This is a full controller reset: both the control state and the
     * derivative history are reset.</p>
     *
     * @param muInit reset value for mu
     */
    public void reset(double muInit) {
        this.mu = Math.max(muMin, Math.min(muMax, muInit));
        this.lastEntropy = Double.NaN;
    }

    /**
     * Reset the controller to a chosen mu value, with optional preservation
     * of entropy history.
     *
     * <p>If clearEntropyHistory is false, the next update still has access
     * to the previous entropy sample and can therefore preserve derivative-like
     * continuity across reset. If true, the derivative term restarts cleanly.</p>
     *
     * @param muInit reset value for mu
     * @param clearEntropyHistory whether to discard the stored entropy history
     */
    public void reset(double muInit, boolean clearEntropyHistory) {
        this.mu = Math.max(muMin, Math.min(muMax, muInit));

        if (clearEntropyHistory) {
            this.lastEntropy = Double.NaN;
        }
    }

    /**
     * Current regulation gain.
     *
     * @return current mu
     */
    public double getMu() {
        return mu;
    }

    /**
     * Set the target entropy for regulation.
     *
     * <p>In the present study this target is understood relative to the
     * von Neumann entropy signal used in update(...).</p>
     *
     * @param target new target entropy
     */
    public void setTargetEntropy(double target) {
        targetEntropy = target;
    }

    /**
     * @return current target entropy
     */
    public double getTargetEntropy(){
        return targetEntropy;
    }

    /**
     * @return multiplicative learning-scale factor applied to alpha0 and beta0
     */
    public double getLearningScale() {
        return learningScale;
    }

    /**
     * Set an external multiplier on the controller update gains.
     *
     * <p>This is useful for intervention experiments in which the controller
     * is temporarily strengthened or weakened without changing its base
     * parametrization.</p>
     *
     * @param learningScale gain multiplier
     */
    public void setLearningScale(double learningScale) {
        this.learningScale = learningScale;
    }
}
