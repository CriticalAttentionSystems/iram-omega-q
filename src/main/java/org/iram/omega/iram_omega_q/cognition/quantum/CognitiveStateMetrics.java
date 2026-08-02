/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.cognition.quantum;
import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.iram.omega.iram_omega_q.cognition.QuantumCognitiveState;

/**
 *
 * @author veronique
 */

/**
 * Utility metrics for analyzing a cognitive state's uncertainty structure.
 *
 * <p>In this project, the density matrix rho is used instrumentally as a
 * compact state descriptor. The quantities below are therefore interpreted
 * as measures of regulatory structure and uncertainty in the model, not as
 * claims about literal physical quantum processes.</p>
 *
 * <p>Three related quantities are used:</p>
 *
 * <ul>
 *   <li><b>von Neumann entropy</b> S(rho): total state uncertainty, computed
 *       from the eigenvalue spectrum of the full density matrix.</li>
 *   <li><b>cognitive entropy</b> Sdiag: uncertainty in the state's diagonal
 *       population distribution only, i.e. uncertainty over explicit basis
 *       occupancies without off-diagonal structure.</li>
 *   <li><b>coherence gap</b> ΔC = Sdiag - SvN: the extra uncertainty present
 *       in the diagonalized description relative to the full state. In this
 *       project, this is used as an operational measure of coherence-related
 *       organization.</li>
 * </ul>
 *
 * <p>Interpretation in this study:</p>
 *
 * <ul>
 *   <li>Higher <b>SvN</b> means the system is more internally disordered or
 *       less sharply regulated.</li>
 *   <li>Higher Sdiag means the state's diagonal population is distributed across more basis states,
 *       rather than concentrated on a small subset of them.</li>
 *   <li>Higher <b>ΔC</b> means the full state retains more structure than is
 *       visible from diagonal occupancies alone; operationally, this is taken
 *       here as greater coherence / organized integration.</li>
 * </ul>
 */
public class CognitiveStateMetrics {

    private static final double EPS = 1e-12;
    private static final double NEGATIVE_EIGEN_TOL = 1e-9;

    /**
     * Compute the von Neumann entropy of the cognitive state:
     *
     * <pre>
     * S_vN(rho) = -Tr(rho log rho) = -sum_i lambda_i log(lambda_i)
     * </pre>
     *
     * The method defensively symmetrizes and trace-normalizes the matrix before
     * computing its spectrum. This makes long simulation sweeps robust against
     * small roundoff asymmetries and near-singular states.
     *
     * @param state cognitive state represented by a density matrix
     * @return von Neumann entropy of the state
     */
    public static double vonNeumannEntropy(CognitiveState state) {
        double[][] rho = state.getDensityMatrix();
        double[][] a = sanitizedSymmetricTraceOneMatrix(rho);
        RealMatrix matrix = new Array2DRowRealMatrix(a, false);

        double[] spectrum;
        try {
            EigenDecomposition eig = new EigenDecomposition(matrix);
            spectrum = eig.getRealEigenvalues();
        } catch (MaxCountExceededException ex) {
            // Eigen decomposition may fail for nearly singular matrices during
            // long sweeps. For a real symmetric positive-semidefinite density
            // matrix, singular values provide a robust spectral fallback.
            SingularValueDecomposition svd = new SingularValueDecomposition(matrix);
            spectrum = svd.getSingularValues();
        }

        spectrum = sanitizeSpectrum(spectrum);
        return entropyFromProbabilities(spectrum);
    }

    /**
     * Compute the diagonal entropy:
     *
     * <pre>
     * S_diag(rho) = -sum_i rho_ii log(rho_ii)
     * </pre>
     *
     * This treats the diagonal entries of rho as a classical probability
     * distribution over the chosen basis.
     *
     * @param state cognitive state represented by a density matrix
     * @return entropy of the diagonal population distribution
     */
    public static double cognitiveEntropy(CognitiveState state) {
        double[][] rho = state.getDensityMatrix();
        int n = rho.length;
        double[] diag = new double[n];
        double sum = 0.0;

        for (int i = 0; i < n; i++) {
            if (rho[i].length != n) {
                throw new IllegalArgumentException("Density matrix must be square");
            }
            double p = rho[i][i];
            if (!Double.isFinite(p)) {
                throw new IllegalStateException("Density matrix diagonal contains NaN or Infinity at " + i);
            }
            // Clamp tiny negative roundoff; larger negatives indicate an invalid state.
            if (p < 0.0 && p > -NEGATIVE_EIGEN_TOL) {
                p = 0.0;
            }
            if (p < 0.0) {
                throw new IllegalStateException("Density matrix diagonal is negative at " + i + ": " + p);
            }
            diag[i] = p;
            sum += p;
        }

        if (!Double.isFinite(sum) || sum < EPS) {
            return 0.0;
        }

        for (int i = 0; i < n; i++) {
            diag[i] /= sum;
        }
        return entropyFromProbabilities(diag);
    }

    /**
     * Compute the coherence gap:
     *
     * <pre>
     * Delta C = S_diag - S_vN
     * </pre>
     *
     * @param state cognitive state represented by a density matrix
     * @return nonnegative coherence-gap measure
     */
    public static double coherenceGap(CognitiveState state) {
        double SvN = vonNeumannEntropy(state);
        double Sdiag = cognitiveEntropy(state);
        return Math.max(0.0, Sdiag - SvN);
    }

    /**
    * Fast von Neumann entropy for the amplitude-state representation.
    *
    * The real-valued analysis density matrix used in this project has the form
    *
    *     rho = r r^T + q q^T
    *
    * where r = Re(psi) and q = Im(psi). This matrix has rank at most two.
    * Its nonzero eigenvalues are therefore the eigenvalues of the 2x2 Gram matrix
    *
    *     [ r.r   r.q ]
    *     [ r.q   q.q ]
    *
    * This gives the same spectral entropy as diagonalizing the full matrix
    * constructed by QuantumCognitiveState.toCognitiveState(), but avoids the
    * expensive d x d eigendecomposition in long sweeps.
    */
   public static double vonNeumannEntropy(QuantumCognitiveState psi) {
       double[] real = psi.real();
       double[] imag = psi.imag();

       if (real.length != imag.length) {
           throw new IllegalArgumentException("Real and imaginary arrays must have same length");
       }

       double a = 0.0; // real · real
       double b = 0.0; // imag · imag
       double c = 0.0; // real · imag

       for (int i = 0; i < real.length; i++) {
           double re = real[i];
           double im = imag[i];

           if (!Double.isFinite(re) || !Double.isFinite(im)) {
               throw new IllegalStateException("QuantumCognitiveState contains NaN or Infinity at " + i);
           }

           a += re * re;
           b += im * im;
           c += re * im;
       }

       double tr = a + b;
       if (!Double.isFinite(tr) || tr <= EPS) {
           throw new IllegalStateException("Invalid QuantumCognitiveState norm: " + tr);
       }

       // Trace-normalize the equivalent 2x2 Gram matrix.
       a /= tr;
       b /= tr;
       c /= tr;

       double disc = (a - b) * (a - b) + 4.0 * c * c;
       if (!Double.isFinite(disc)) {
           throw new IllegalStateException("Invalid Gram discriminant: " + disc);
       }
       disc = Math.max(0.0, disc);

       double root = Math.sqrt(disc);
       double lambdaPlus = 0.5 * ((a + b) + root);
       double lambdaMinus = 0.5 * ((a + b) - root);

       double[] spectrum = sanitizeSpectrum(new double[] { lambdaPlus, lambdaMinus });
       return entropyFromProbabilities(spectrum);
   }

   /**
    * Fast diagonal/cognitive entropy for the amplitude-state representation.
    *
    * The diagonal entries of the real-valued analysis density matrix are
    *
    *     rho_ii = real_i^2 + imag_i^2.
    *
    * Therefore the diagonal entropy can be computed directly from the amplitudes
    * without constructing the full matrix.
    */
    public static double cognitiveEntropy(QuantumCognitiveState psi) {
       double[] real = psi.real();
       double[] imag = psi.imag();

       if (real.length != imag.length) {
           throw new IllegalArgumentException("Real and imaginary arrays must have same length");
       }

       double[] probs = new double[real.length];
       double sum = 0.0;

       for (int i = 0; i < real.length; i++) {
           double re = real[i];
           double im = imag[i];

           if (!Double.isFinite(re) || !Double.isFinite(im)) {
               throw new IllegalStateException("QuantumCognitiveState contains NaN or Infinity at " + i);
           }

           double p = re * re + im * im;
           probs[i] = p;
           sum += p;
       }

       if (!Double.isFinite(sum) || sum <= EPS) {
           return 0.0;
       }

       for (int i = 0; i < probs.length; i++) {
           probs[i] /= sum;
       }

       return entropyFromProbabilities(probs);
    }

   /**
    * Fast coherence gap for the amplitude-state representation.
    *
    * Delta C = S_diag - S_vN.
    */
    public static double coherenceGap(QuantumCognitiveState psi) {
       double SvN = vonNeumannEntropy(psi);
       double Sdiag = cognitiveEntropy(psi);
       return Math.max(0.0, Sdiag - SvN);
    }
    
    private static double spectralEntropy(double lambda) {
        if (!Double.isFinite(lambda)) {
            throw new IllegalStateException("Invalid eigenvalue: " + lambda);
        }

        if (lambda < 0.0 && lambda > -1e-12) {
            lambda = 0.0;
        }

        if (lambda <= 1e-12) {
            return 0.0;
        }

        return -lambda * Math.log(lambda);
    }
    
    private static double[][] sanitizedSymmetricTraceOneMatrix(double[][] rho) {
        if (rho == null || rho.length == 0) {
            throw new IllegalArgumentException("Density matrix is empty");
        }

        int n = rho.length;
        double[][] a = new double[n][n];

        for (int i = 0; i < n; i++) {
            if (rho[i] == null || rho[i].length != n) {
                throw new IllegalArgumentException("Density matrix must be square");
            }
        }

        // Symmetrize defensively. The analysis representation should be real
        // symmetric; this removes small numerical asymmetries before spectral analysis.
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double vij = rho[i][j];
                double vji = rho[j][i];
                if (!Double.isFinite(vij) || !Double.isFinite(vji)) {
                    throw new IllegalStateException(
                        "Density matrix contains NaN or Infinity at (" + i + "," + j + ")"
                    );
                }
                a[i][j] = 0.5 * (vij + vji);
            }
        }

        double trace = 0.0;
        for (int i = 0; i < n; i++) {
            trace += a[i][i];
        }

        if (!Double.isFinite(trace) || Math.abs(trace) < EPS) {
            throw new IllegalStateException("Density matrix has invalid trace: " + trace);
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                a[i][j] /= trace;
            }
        }

        return a;
    }

    private static double[] sanitizeSpectrum(double[] spectrum) {
        double[] p = new double[spectrum.length];
        double sum = 0.0;

        for (int i = 0; i < spectrum.length; i++) {
            double x = spectrum[i];
            if (!Double.isFinite(x)) {
                throw new IllegalStateException("Spectrum contains NaN or Infinity: " + x);
            }
            if (x < 0.0 && x > -NEGATIVE_EIGEN_TOL) {
                x = 0.0;
            }
            if (x < 0.0) {
                // For publication sweeps, avoid killing a run on small numerical PSD violations.
                // If this occurs frequently, the state update should be investigated.
                x = 0.0;
            }
            p[i] = x;
            sum += x;
        }

        if (!Double.isFinite(sum) || sum < EPS) {
            return p;
        }

        for (int i = 0; i < p.length; i++) {
            p[i] /= sum;
        }
        return p;
    }

    private static double entropyFromProbabilities(double[] p) {
        double entropy = 0.0;
        for (double x : p) {
            if (x > EPS) {
                entropy -= x * Math.log(x);
            }
        }
        return entropy;
    }
    
    public static double coherenceGap(QuantumCognitiveState psi, double SvN) {
        double Sdiag = cognitiveEntropy(psi);
        return Math.max(0.0, Sdiag - SvN);
    }
}
