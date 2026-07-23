/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.cognition;

/**
 *
 * @author veronique
 */
/**
 * Utility class that applies a simple mindfulness-style stabilization
 * operation to a quantum-like cognitive state.
 *
 * This operator models the idea of gently strengthening
 * one chosen focus state by damping all competing states.
 *
 * It does not directly increase the focus state itself. Instead, it reduces
 * the amplitudes of all other basis states and then normalizes the whole
 * state. After normalization, the chosen focus state becomes relatively more
 * dominant.
 *
 * Interpretation:
 *
 *     focusIndex:
 *         The cognitive state being attended to.
 *
 *     strength:
 *         How strongly competing states are suppressed.
 *
 * Example:
 *
 *     If focusIndex represents attention on the breath, then this operator
 *     reduces the influence of non-breath-related states and renormalizes the
 *     cognitive state around the remaining focus.
 *
 * This is a modeling abstraction. It is not claiming that mindfulness literally
 * performs this exact mathematical operation in the brain.
 */
public class MindfulnessOperator {

    /**
     * Stabilizes the cognitive state around a selected focus index.
     *
     * Workflow:
     *
     *     1. Loop over every basis state in psi.
     *     2. Leave the selected focusIndex unchanged.
     *     3. Multiply every non-focus state's real and imaginary amplitude by
     *        (1.0 - strength).
     *     4. Normalize the state so total probability remains valid.
     *
     * Effect:
     *
     *     Non-focus components become smaller.
     *     The focus component becomes relatively more important.
     *     The state remains normalized after the operation.
     *
     * @param psi quantum-like cognitive state to modify in place
     * @param focusIndex basis-state index selected as the attentional focus
     * @param strength damping strength applied to non-focus components
     */
    public static void stabilize(
        QuantumCognitiveState psi,
        int focusIndex,
        double strength
    ) {
        /*
         * Visit each component of the cognitive state.
         *
         * The state is represented by real and imaginary amplitude arrays.
         * Each index corresponds to one cognitive basis state.
         */
        for (int i = 0; i < psi.dim(); i++) {

            /*
             * Leave the chosen focus state untouched.
             *
             * All other states are treated as competing or distracting
             * components and are damped.
             */
            if (i != focusIndex) {

                /*
                 * Reduce the real part of the non-focus amplitude.
                 */
                psi.real()[i] *= (1.0 - strength);

                /*
                 * Reduce the imaginary part of the non-focus amplitude.
                 */
                psi.imag()[i] *= (1.0 - strength);
            }
        }

        /*
         * Renormalize the state.
         *
         * Damping changes the total magnitude of the state vector, so
         * normalization is required to keep the state mathematically valid.
         *
         * After normalization, the focus state has greater relative weight.
         */
        psi.normalize();
    }
}