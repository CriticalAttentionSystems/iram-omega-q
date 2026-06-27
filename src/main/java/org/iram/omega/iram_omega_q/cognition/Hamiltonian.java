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
 * Represents the Hamiltonian used to drive the internal dynamics of the
 * quantum-like cognitive state.
 *
 * In this model, the Hamiltonian defines how strongly different cognitive
 * basis states interact with one another.
 *
 * The matrix element H(i, j) has two meanings:
 *
 *     i == j:
 *         The diagonal value represents the intrinsic energy or bias of
 *         basis state i.
 *
 *     i != j:
 *         The off-diagonal value represents coupling between basis states
 *         i and j. Larger coupling means the state can more easily flow,
 *         mix, or transition between those two cognitive components.
 *
 * For exact coherent evolution through exp(-i H dt), implementations used
 * by this simulation must be real symmetric, H(i,j) = H(j,i), so that H is
 * Hermitian and generates a unitary propagator.
 *
 * This is not claiming that the brain is literally quantum. The Hamiltonian
 * is used here as a compact mathematical structure for modeling structured
 * state evolution.
 */
public interface Hamiltonian {

    /**
     * Returns the Hamiltonian matrix element H(i, j).
     *
     * @param i row index / target basis state
     * @param j column index / source basis state
     * @return interaction strength between basis states i and j
     */
    double get(int i, int j);

    /**
     * Builds an attentional Hamiltonian.
     *
     * This helper creates a Hamiltonian where:
     *
     * 1. Each basis state has its own intrinsic energy on the diagonal.
     *
     *        H(i, i) = energies[i]
     *
     * 2. Different basis states are coupled off-diagonal.
     *
     *        H(i, j) = coupling * exp(-distance / lengthScale)
     *
     *    where:
     *
     *        distance = |i - j|
     *
     * This means nearby cognitive states interact more strongly than distant
     * ones. The farther apart two states are in the basis, the weaker their
     * direct interaction becomes.
     *
     * Interpretation:
     *
     *     The agent's attention can move or spread between related cognitive
     *     states more easily than between unrelated or distant ones.
     *
     * Parameters:
     *
     *     energies:
     *         Diagonal energy/bias values for each cognitive basis state.
     *
     *     coupling:
     *         Overall strength of interaction between different basis states.
     *
     *     lengthScale:
     *         Controls how quickly interaction strength decays with distance.
     *         A small value makes interactions very local.
     *         A large value allows broader interaction across the state space.
     *
     * @param energies intrinsic energy values for each basis state
     * @param coupling off-diagonal coupling strength
     * @param lengthScale distance scale for coupling decay
     * @return a Hamiltonian with local attentional coupling
     */
    static Hamiltonian attentional(
            double[] energies,
            double coupling,
            double lengthScale
    ) {
        int dim = energies.length;

        return (i, j) -> {
            /*
             * Diagonal element:
             *
             * The state interacts with itself according to its intrinsic energy
             * or bias.
             */
            if (i == j) {
                return energies[i];
            }

            /*
             * Off-diagonal element:
             *
             * Compute how far apart the two basis states are.
             */
            double dist = Math.abs(i - j);

            /*
             * Coupling decays exponentially with distance.
             *
             * Nearby states have stronger coupling.
             * Distant states have weaker coupling.
             */
            return coupling * Math.exp(-dist / lengthScale);
        };
    }
}
