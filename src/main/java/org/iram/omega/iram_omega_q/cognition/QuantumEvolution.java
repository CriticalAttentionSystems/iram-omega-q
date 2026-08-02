/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.cognition;

/**
 * Exact coherent evolution for the quantum-like cognitive state.
 *
 * The previous implementation applied a first-order Euler approximation to
 * the Schrödinger equation and then normalized the state. This class now
 * applies the finite-time unitary propagator
 *
 *     psi(t + dt) = exp(-i H dt) psi(t).
 *
 * For the real symmetric Hamiltonians used by the simulation, H is
 * diagonalized as H = V D V^T and the exponential is
 *
 *     exp(-i H dt) = V exp(-i D dt) V^T.
 *
 * @author veronique
 */
public final class QuantumEvolution {

    /** Numerical symmetry tolerance for a real Hermitian Hamiltonian. */
    private static final double SYMMETRY_TOLERANCE = 1.0e-12;

    /** Convergence threshold for Jacobi diagonalization. */
    private static final double DIAGONALIZATION_TOLERANCE = 1.0e-14;

    private QuantumEvolution() {
        // Utility class.
    }

    /**
     * Compatibility entry point for one exact unitary evolution step.
     *
     * This method retains the original public API. For repeated evolution
     * under a fixed H and dt, construct a {@link Propagator} once and reuse
     * it, as QuantumRegulationAgent now does.
     *
     * @param psi quantum-like cognitive state to evolve in place
     * @param H Hamiltonian generating coherent evolution
     * @param dt finite time increment
     */
    public static void step(
            QuantumCognitiveState psi,
            Hamiltonian H,
            double dt
    ) {
        if (psi == null) {
            throw new IllegalArgumentException("psi must not be null");
        }
        new Propagator(H, psi.dim(), dt).apply(psi);
    }

    /**
     * Precomputed exact unitary propagator U = exp(-i H dt).
     *
     * A propagator should be constructed once for a fixed Hamiltonian,
     * dimension, and time step, then applied at each cognitive step.
     */
    public static final class Propagator {

        private final int dim;
        private final double[][] realPart;
        private final double[][] imagPart;

        /**
         * Construct U = exp(-i H dt) for a real symmetric Hamiltonian.
         *
         * @param H Hamiltonian accessed through the existing get(i,j) API
         * @param dim state-space dimension
         * @param dt finite time increment
         */
        public Propagator(Hamiltonian H, int dim, double dt) {
            if (H == null) {
                throw new IllegalArgumentException("H must not be null");
            }
            if (dim <= 0) {
                throw new IllegalArgumentException("dim must be positive");
            }
            if (!Double.isFinite(dt) || dt <= 0.0) {
                throw new IllegalArgumentException("dt must be finite and positive");
            }

            this.dim = dim;

            double[][] matrix = readSymmetricHamiltonian(H, dim);
            EigenSystem eigen = diagonalizeSymmetric(matrix);

            this.realPart = new double[dim][dim];
            this.imagPart = new double[dim][dim];

            /*
             * U_ij = sum_k V_ik exp(-i lambda_k dt) V_jk.
             *
             * Since V and lambda are real, construct the real and imaginary
             * parts directly without introducing a complex-matrix dependency.
             */
            for (int k = 0; k < dim; k++) {
                double phase = eigen.values[k] * dt;
                double cos = Math.cos(phase);
                double minusSin = -Math.sin(phase);

                for (int i = 0; i < dim; i++) {
                    double vik = eigen.vectors[i][k];

                    for (int j = 0; j < dim; j++) {
                        double projection = vik * eigen.vectors[j][k];
                        realPart[i][j] += projection * cos;
                        imagPart[i][j] += projection * minusSin;
                    }
                }
            }
        }

        /**
         * Apply the precomputed exact unitary update in place.
         *
         * @param psi state to transform as psi <- U psi
         */
        public void apply(QuantumCognitiveState psi) {
            if (psi == null) {
                throw new IllegalArgumentException("psi must not be null");
            }
            if (psi.dim() != dim) {
                throw new IllegalArgumentException(
                        "State dimension does not match unitary propagator dimension");
            }

            double[] r = psi.real();
            double[] im = psi.imag();

            double[] newR = new double[dim];
            double[] newI = new double[dim];

            for (int i = 0; i < dim; i++) {
                for (int j = 0; j < dim; j++) {
                    /*
                     * (Ur + i Ui)(r + i im)
                     *   = (Ur*r - Ui*im) + i(Ur*im + Ui*r).
                     */
                    newR[i] += realPart[i][j] * r[j]
                            - imagPart[i][j] * im[j];
                    newI[i] += realPart[i][j] * im[j]
                            + imagPart[i][j] * r[j];
                }
            }

            System.arraycopy(newR, 0, r, 0, dim);
            System.arraycopy(newI, 0, im, 0, dim);

            /*
             * U is unitary and therefore preserves norm. normalize() removes
             * only negligible floating-point drift accumulated in long runs.
             */
            psi.normalize();
        }
    }

    /**
     * Read H through the current interface and require the real-Hermitian
     * condition needed for unitary evolution: H(i,j) = H(j,i).
     */
    private static double[][] readSymmetricHamiltonian(Hamiltonian H, int dim) {
        double[][] matrix = new double[dim][dim];

        for (int i = 0; i < dim; i++) {
            for (int j = i; j < dim; j++) {
                double hij = H.get(i, j);
                double hji = H.get(j, i);

                if (!Double.isFinite(hij) || !Double.isFinite(hji)) {
                    throw new IllegalArgumentException(
                            "Hamiltonian contains a non-finite value");
                }

                double scale = Math.max(1.0, Math.max(Math.abs(hij), Math.abs(hji)));
                if (Math.abs(hij - hji) > SYMMETRY_TOLERANCE * scale) {
                    throw new IllegalArgumentException(
                            "Hamiltonian must be real symmetric for unitary evolution: "
                            + "H(" + i + "," + j + ") != H(" + j + "," + i + ")");
                }

                // Average values differing only by harmless floating-point noise.
                double value = 0.5 * (hij + hji);
                matrix[i][j] = value;
                matrix[j][i] = value;
            }
        }

        return matrix;
    }

    /**
     * Diagonalize a real symmetric matrix with Jacobi rotations.
     *
     * This avoids adding an external linear-algebra dependency to the current
     * Maven/NetBeans project while remaining suitable for the small state
     * spaces used by the simulation.
     */
    private static EigenSystem diagonalizeSymmetric(double[][] input) {
        int n = input.length;

        double[][] a = new double[n][n];
        double[][] vectors = new double[n][n];

        for (int i = 0; i < n; i++) {
            System.arraycopy(input[i], 0, a[i], 0, n);
            vectors[i][i] = 1.0;
        }

        int maxIterations = Math.max(32, 100 * n * n);

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            int p = 0;
            int q = 0;
            double largestOffDiagonal = 0.0;

            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double magnitude = Math.abs(a[i][j]);
                    if (magnitude > largestOffDiagonal) {
                        largestOffDiagonal = magnitude;
                        p = i;
                        q = j;
                    }
                }
            }

            if (largestOffDiagonal < DIAGONALIZATION_TOLERANCE) {
                double[] values = new double[n];
                for (int i = 0; i < n; i++) {
                    values[i] = a[i][i];
                }
                return new EigenSystem(values, vectors);
            }

            double app = a[p][p];
            double aqq = a[q][q];
            double apq = a[p][q];

            double theta = 0.5 * Math.atan2(2.0 * apq, aqq - app);
            double c = Math.cos(theta);
            double s = Math.sin(theta);

            for (int k = 0; k < n; k++) {
                if (k != p && k != q) {
                    double akp = a[k][p];
                    double akq = a[k][q];

                    a[k][p] = c * akp - s * akq;
                    a[p][k] = a[k][p];

                    a[k][q] = s * akp + c * akq;
                    a[q][k] = a[k][q];
                }
            }

            a[p][p] = c * c * app - 2.0 * s * c * apq + s * s * aqq;
            a[q][q] = s * s * app + 2.0 * s * c * apq + c * c * aqq;
            a[p][q] = 0.0;
            a[q][p] = 0.0;

            for (int k = 0; k < n; k++) {
                double vkp = vectors[k][p];
                double vkq = vectors[k][q];

                vectors[k][p] = c * vkp - s * vkq;
                vectors[k][q] = s * vkp + c * vkq;
            }
        }

        throw new IllegalStateException(
                "Jacobi diagonalization failed to converge for Hamiltonian");
    }

    /** Eigenvalues and column eigenvectors for a real symmetric matrix. */
    private static final class EigenSystem {

        private final double[] values;
        private final double[][] vectors;

        private EigenSystem(double[] values, double[][] vectors) {
            this.values = values;
            this.vectors = vectors;
        }
    }
}
