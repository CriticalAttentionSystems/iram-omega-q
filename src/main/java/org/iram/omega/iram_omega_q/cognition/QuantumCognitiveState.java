/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.cognition;

import java.util.Random;
import org.apache.commons.math3.complex.Complex;
import org.iram.omega.iram_omega_q.cognition.quantum.CognitiveState;

/**
 *
 * @author veronique
 */
public class QuantumCognitiveState {

    /*
     * Real and imaginary parts of the quantum-like state vector.
     *
     * Each index represents one cognitive basis state.
     *
     * The full complex amplitude at basis index i is:
     *
     *     psi_i = real[i] + i * imag[i]
     *
     * The probability-like weight of basis state i is:
     *
     *     |psi_i|^2 = real[i]^2 + imag[i]^2
     *
     * In this project, this is a mathematical representation of a cognitive
     * state. It is not a claim that the brain is literally using quantum
     * amplitudes.
     */
    private final double[] real;
    private final double[] imag;

    /**
     * Creates a quantum-like cognitive state with the requested dimension.
     *
     * The dimension is the number of cognitive basis states available to the
     * model.
     *
     * By default, the state is initialized as:
     *
     *     |0>
     *
     * meaning all amplitude is placed in basis state 0.
     *
     * In array form:
     *
     *     real[0] = 1.0
     *     imag[0] = 0.0
     *     all other entries = 0.0
     *
     * @param dim number of cognitive basis states
     */
    public QuantumCognitiveState(int dim) {
        real = new double[dim];
        imag = new double[dim];

        /*
         * Start in the first basis state.
         *
         * This is the simplest possible normalized initial condition.
         */
        real[0] = 1.0; // |0⟩ initial state
    }

    /**
     * Returns the number of cognitive basis states.
     *
     * @return dimension of the state vector
     */
    public int dim() {
        return real.length;
    }

    /**
     * Direct access to the real part of the state vector.
     *
     * This returns the internal array, not a copy. That means callers can
     * modify the state directly.
     *
     * @return real amplitude array
     */
    public double[] real() {
        return real;
    }

    /**
     * Direct access to the imaginary part of the state vector.
     *
     * This returns the internal array, not a copy. That means callers can
     * modify the state directly.
     *
     * @return imaginary amplitude array
     */
    public double[] imag() {
        return imag;
    }

    /**
     * Renormalizes the state vector so that the total probability-like weight
     * equals 1.
     *
     * Mathematically, this enforces:
     *
     *     Σ_i |psi_i|^2 = 1
     *
     * where:
     *
     *     |psi_i|^2 = real[i]^2 + imag[i]^2
     *
     * Why this matters:
     *
     *     Many operations change the size of the vector. Normalization keeps
     *     the state valid as a probability-like representation.
     */
    public void normalize() {
        double norm = 0;

        /*
         * Compute squared norm:
         *
         *     norm = Σ_i real[i]^2 + imag[i]^2
         */
        for (int i = 0; i < real.length; i++) {
            norm += real[i] * real[i] + imag[i] * imag[i];
        }

        /*
         * A zero-norm state cannot be normalized.
         *
         * This would mean all amplitudes are zero, so the model no longer has
         * a valid state.
         */
        if (norm == 0.0) {
            throw new IllegalStateException("QuantumCognitiveState has zero norm");
        }

        /*
         * Convert squared norm into ordinary Euclidean norm.
         */
        norm = Math.sqrt(norm);

        /*
         * Divide every complex amplitude by the norm.
         *
         * After this loop:
         *
         *     Σ_i |psi_i|^2 = 1
         */
        for (int i = 0; i < real.length; i++) {
            real[i] /= norm;
            imag[i] /= norm;
        }
    }

    /**
     * Converts this pure quantum-like state vector into a CognitiveState
     * represented by a density matrix.
     *
     * Conceptually, this builds:
     *
     *     rho = |psi><psi|
     *
     * The density matrix is then used by CognitiveStateMetrics to compute:
     *
     * von Neumann entropy diagonal cognitive entropy coherence gap
     *
     * Note:
     *
     * The code below constructs a real-valued approximation of the outer
     * product. For a fully complex density matrix, the off-diagonal element
     * would normally involve the complex conjugate:
     *
     * rho_ij = psi_i * conjugate(psi_j)
     *
     * Here the implementation stores only a real matrix, so it uses a
     * simplified real-valued representation.
     *
     * @return CognitiveState density-matrix representation of this state
     */
    public CognitiveState toCognitiveState() {
        int n = dim();
        CognitiveState cs = new CognitiveState(n);

        double[][] rho = cs.getDensityMatrix();

        /*
         * Fill the density matrix from pairwise products of amplitudes.
         *
         * Diagonal entries rho[i][i] behave like population weights.
         * Off-diagonal entries rho[i][j] represent coherence-like structure
         * between basis states i and j.
         */
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                rho[i][j] =
                    real[i] * real[j] +
                    imag[i] * imag[j];
            }
        }

        /*
         * Normalize the density matrix so that it is valid for metric
         * calculations.
         */
        cs.normalize();

        return cs;
    }

    /**
     * Builds a coherent quantum-like cognitive state from a salience profile.
     *
     * The salience profile says which basis states are initially important.
     *
     * Example:
     *
     *     high salience near index 6
     *
     * means:
     *
     *     the agent begins with attention concentrated around basis state 6.
     *
     * Workflow:
     *
     *     1. Normalize salience values into weights.
     *     2. Convert each weight into an amplitude using sqrt(weight).
     *     3. Add a random phase to each amplitude.
     *     4. Normalize the final state.
     *
     * Why sqrt(weight)?
     *
     *     In amplitude-based representations:
     *
     *         probability-like weight = amplitude^2
     *
     *     Therefore:
     *
     *         amplitude = sqrt(weight)
     *
     * @param salience non-negative importance values over basis states
     * @param phaseNoiseStd standard deviation of random phase noise
     * @param rng seeded random generator for reproducible phases
     * @return normalized quantum-like cognitive state
     */
    public static QuantumCognitiveState salienceCoherent(
        double[] salience,
        double phaseNoiseStd,
        Random rng
    ) {
        int dim = salience.length;
        QuantumCognitiveState psi = new QuantumCognitiveState(dim);

        /*
         * Compute total salience so the profile can be converted into a
         * normalized distribution of weights.
         */
        double sum = 0.0;
        for (double s : salience) {
            sum += s;
        }

        /*
         * If total salience is zero, there is no meaningful way to construct
         * an initial attentional distribution.
         */
        if (sum == 0) {
            throw new IllegalArgumentException("Zero salience");
        }

        /*
         * Convert salience profile into complex amplitudes.
         */
        for (int i = 0; i < dim; i++) {

            /*
             * Convert raw salience into normalized probability-like weight.
             */
            double w = salience[i] / sum;

            /*
             * Convert weight into amplitude.
             *
             * Squaring this amplitude recovers the weight.
             */
            double amp = Math.sqrt(w);

            /*
             * Draw a small random phase.
             *
             * Larger phaseNoiseStd means the initial state has more random
             * phase variation across basis components.
             */
            double theta = phaseNoiseStd * rng.nextGaussian();

            /*
             * Store the complex amplitude:
             *
             *     psi_i = amp * exp(i theta)
             *
             * which equals:
             *
             *     real = amp * cos(theta)
             *     imag = amp * sin(theta)
             */
            psi.real[i] = amp * Math.cos(theta);
            psi.imag[i] = amp * Math.sin(theta);
        }

        /*
         * Normalize defensively to remove any small numerical drift.
         */
        psi.normalize();

        return psi;
    }

    /**
     * Creates a deep copy of this cognitive state.
     *
     * The returned QuantumCognitiveState has its own real and imaginary arrays.
     * Changing the copy will not modify the original.
     *
     * @return independent copy of this state
     */
    public QuantumCognitiveState copy() {
        QuantumCognitiveState q = new QuantumCognitiveState(this.dim());

        /*
         * Copy every complex amplitude.
         */
        for (int i = 0; i < this.dim(); i++) {
            q.real()[i] = this.real[i];
            q.imag()[i] = this.imag[i];
        }

        return q;
    }

    /**
     * Applies random phase perturbations to the state.
     *
     * This changes the phase of each complex amplitude while preserving its
     * magnitude before normalization.
     *
     * Conceptually:
     *
     *     psi_i -> psi_i * exp(i theta_i)
     *
     * where theta_i is sampled from a Gaussian distribution:
     *
     *     theta_i = epsilon * rng.nextGaussian()
     *
     * Interpretation:
     *
     *     This models a disturbance that scrambles phase relationships between
     *     cognitive basis states without directly changing their population
     *     weights.
     *
     * @param epsilon strength / standard deviation of phase perturbation
     * @param rng seeded random generator for reproducible perturbations
     */
    public void perturbPhase(double epsilon, Random rng) {
        for (int i = 0; i < this.dim(); i++) {

            /*
             * Draw random phase angle for this basis component.
             */
            double theta = epsilon * rng.nextGaussian();

            /*
             * Reconstruct the current complex amplitude.
             */
            Complex psi = new Complex(this.real[i], this.imag[i]);

            /*
             * Multiply by exp(i theta):
             *
             *     exp(i theta) = cos(theta) + i sin(theta)
             *
             * This rotates the amplitude in the complex plane.
             */
            psi = psi.multiply(
                new Complex(Math.cos(theta), Math.sin(theta))
            );

            /*
             * Store the rotated amplitude back into the state arrays.
             */
            this.real[i] = psi.getReal();
            this.imag[i] = psi.getImaginary();
        }

        /*
         * Normalize after the perturbation to protect against numerical drift.
         */
        normalize();
    }
    
    public static double vonNeumannEntropy(QuantumCognitiveState psi) {
        double[] re = psi.real();
        double[] im = psi.imag();

        double a = 0.0;
        double b = 0.0;
        double c = 0.0;

        for (int i = 0; i < re.length; i++) {
            a += re[i] * re[i];
            b += im[i] * im[i];
            c += re[i] * im[i];
        }

        double tr = a + b;
        if (tr <= 0.0 || !Double.isFinite(tr)) {
            throw new IllegalStateException("Invalid state norm in entropy calculation");
        }

        a /= tr;
        b /= tr;
        c /= tr;

        double disc = (a - b) * (a - b) + 4.0 * c * c;
        disc = Math.max(0.0, disc);

        double root = Math.sqrt(disc);
        double lambdaPlus = 0.5 * ((a + b) + root);
        double lambdaMinus = 0.5 * ((a + b) - root);

        return entropy2(lambdaPlus, lambdaMinus);
    }

    private static double entropy2(double l1, double l2) {
        double s = 0.0;

        if (l1 > 1e-12) {
            s -= l1 * Math.log(l1);
        }

        if (l2 > 1e-12) {
            s -= l2 * Math.log(l2);
        }

        return s;
    }
    
    public static double diagonalEntropy(QuantumCognitiveState psi) {
        double[] re = psi.real();
        double[] im = psi.imag();

        double norm = 0.0;
        for (int i = 0; i < re.length; i++) {
            norm += re[i] * re[i] + im[i] * im[i];
        }

        if (norm <= 0.0 || !Double.isFinite(norm)) {
            throw new IllegalStateException("Invalid state norm in diagonal entropy");
        }

        double s = 0.0;
        for (int i = 0; i < re.length; i++) {
            double p = (re[i] * re[i] + im[i] * im[i]) / norm;
            if (p > 1e-12) {
                s -= p * Math.log(p);
            }
        }

        return s;
    }
    
    public static double coherenceGap(QuantumCognitiveState psi) {
        return diagonalEntropy(psi) - vonNeumannEntropy(psi);
    }
    
    
}