/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.iram.omega.iram_omega_q.simulation.ConsciousSimulation;
import org.iram.omega.iram_omega_q.simulation.SimulationParameters;
import org.iram.omega.iram_omega_q.simulation.Util;


/**
 *
 * @author veronique
 */

/**
 * Utilities for estimating the stability of the critical regulation point μc.
 *
 * <p>Interpretation in this project:</p>
 * <ul>
 *   <li>For a fixed noise level η, we scan across a grid of μ values.</li>
 *   <li>At each μ, we run multiple simulations and compute a per-run summary
 *       observable (here: mean coherence after burn-in).</li>
 *   <li>The across-run variance of that observable is treated as a
 *       susceptibility-like quantity.</li>
 *   <li>The μ value where this susceptibility is maximal is taken as the
 *       critical point μc(η).</li>
 * </ul>
 *
 * <p>This class adds uncertainty quantification to that estimate by bootstrapping
 * over runs. It returns:</p>
 * <ul>
 *   <li>the point estimate μc,</li>
 *   <li>the bootstrap standard deviation of μc,</li>
 *   <li>a bootstrap 95% confidence interval,</li>
 *   <li>and the number of runs per μ actually used.</li>
 * </ul>
 */
public class MuCStability {

    /**
     * Result container for a μc estimate at one fixed noise level.
     */
    public static final class Result {
        /** Point estimate of the critical regulation value μc. */
        public final double muC;

        /** Bootstrap standard deviation of μc. */
        public final double sdMuC;

        /** Lower bound of the bootstrap 95% confidence interval. */
        public final double ciLow;

        /** Upper bound of the bootstrap 95% confidence interval. */
        public final double ciHigh;

        /** Number of runs per μ used when the estimate stopped. */
        public final int runsPerPointUsed;

        public Result(double muC, double sdMuC, double ciLow, double ciHigh, int runsPerPointUsed) {
            this.muC = muC;
            this.sdMuC = sdMuC;
            this.ciLow = ciLow;
            this.ciHigh = ciHigh;
            this.runsPerPointUsed = runsPerPointUsed;
        }
    }

    /**
     * Estimate μc(η) and quantify its uncertainty by bootstrap resampling.
     *
     * <p>This method works for one fixed noise value etaFixed. It scans over
     * the supplied μ grid, runs multiple simulations per μ, computes a
     * susceptibility-like quantity from across-run variance, and identifies
     * μc as the μ value with maximal susceptibility.</p>
     *
     * <p>Observable used here:</p>
     * <ul>
     *   <li>For each run, we compute the mean coherence after burn-in.</li>
     *   <li>Across runs at fixed μ, we compute the sample variance of that
     *       mean coherence.</li>
     *   <li>This variance is treated as a susceptibility proxy.</li>
     * </ul>
     *
     * <p>Bootstrap logic:</p>
     * <ul>
     *   <li>Resample the run-level values with replacement.</li>
     *   <li>Recompute susceptibility across μ.</li>
     *   <li>Recompute μc for each bootstrap replicate.</li>
     *   <li>Use the resulting μc distribution to estimate SD and 95% CI.</li>
     * </ul>
     *
     * <p>Stopping rule:</p>
     * <ul>
     *   <li>Stop once sdMuC <= 0.5 * dMuGrid</li>
     *   <li>or once runsMax is reached.</li>
     * </ul>
     *
     * <p>This means we stop when the uncertainty in μc is smaller than half
     * a μ-grid spacing, i.e. when μc is resolved more tightly than the
     * underlying scan resolution.</p>
     *
     * @param base base simulation parameters
     * @param etaFixed fixed noise level η at which μc is estimated
     * @param muGrid grid of μ values to scan
     * @param burnIn number of recorded samples to discard at the beginning
     * @param runsStart initial number of runs per μ
     * @param runsStep number of runs to add per refinement step
     * @param runsMax maximum number of runs per μ
     * @param bootstrapB number of bootstrap replicates
     * @param rng random generator for bootstrap resampling
     * @return μc estimate with uncertainty summary
     */
    public static Result estimateMuCWithBootstrap(
            SimulationParameters base,
            double etaFixed,
            double[] muGrid,
            int burnIn,
            int runsStart,
            int runsStep,
            int runsMax,
            int bootstrapB,
            Random rng
    ) {
        // μ-grid spacing used in the stopping rule.
        final double dMu = (muGrid.length >= 2) ? (muGrid[1] - muGrid[0]) : 1.0;

        // One bucket per μ value.
        // Each bucket stores the per-run mean coherence values accumulated so far.
        ArrayList<double[]> samplesByMu = new ArrayList<>(muGrid.length);
        for (int i = 0; i < muGrid.length; i++) {
            samplesByMu.add(new double[0]);
        }

        int R = 0;

        while (true) {
            // Grow the number of runs per μ incrementally.
            int targetR = (R == 0) ? runsStart : Math.min(runsMax, R + runsStep);
            if (targetR == R) break; // nothing left to add

            // Extend each μ bucket to targetR samples.
            for (int i = 0; i < muGrid.length; i++) {
                double[] old = samplesByMu.get(i);
                double[] neu = Arrays.copyOf(old, targetR);

                // Fill only the newly added run slots.
                for (int r = old.length; r < targetR; r++) {
                    SimulationParameters p = base.copy();
                    p.emotionalNoise = etaFixed;
                    p.muInit = muGrid[i];

                    // Deterministic seed mixing consistent with your sweep conventions.
                    p.seed = Util.mixSeed(
                            p.baseSeed,
                            i,                  // μ index
                            0,                  // noise index fixed -> 0 in this 1D slice
                            r                  // run index
                    );

                    ConsciousSimulation.SimulationResult res = ConsciousSimulation.run(p);

                    int steps = res.coherence.size();

                    // If the run is too short after burn-in, mark as unusable.
                    if (steps <= burnIn + 1) {
                        neu[r] = Double.NaN;
                        continue;
                    }

                    // Per-run summary observable:
                    // mean coherence over the post-burn-in window.
                    double mean = 0.0;
                    int n = 0;
                    for (int t = burnIn; t < steps; t++) {
                        mean += res.coherence.get(t);
                        n++;
                    }

                    neu[r] = (n > 0) ? (mean / n) : Double.NaN;
                }

                samplesByMu.set(i, neu);
            }

            R = targetR;

            // Compute susceptibility-like variance across runs at each μ.
            double[] chi = susceptibilityAcrossRuns(samplesByMu);

            // Point estimate of μc = μ at maximal susceptibility.
            int idxMuC = argmaxFinite(chi);
            double muC = (idxMuC >= 0) ? muGrid[idxMuC] : Double.NaN;

            // Bootstrap distribution of μc.
            double[] muCboot = new double[bootstrapB];

            for (int b = 0; b < bootstrapB; b++) {
                double[] chiBoot = new double[muGrid.length];

                for (int i = 0; i < muGrid.length; i++) {
                    double[] x = samplesByMu.get(i);

                    // Use only finite run summaries.
                    double[] finite = Arrays.stream(x).filter(Double::isFinite).toArray();
                    if (finite.length < 2) {
                        chiBoot[i] = Double.NaN;
                        continue;
                    }

                    // Bootstrap resample with replacement.
                    double[] draw = new double[finite.length];
                    for (int k = 0; k < draw.length; k++) {
                        draw[k] = finite[rng.nextInt(finite.length)];
                    }

                    // Susceptibility proxy for this bootstrap sample.
                    chiBoot[i] = sampleVariance(draw);
                }

                int idx = argmaxFinite(chiBoot);
                muCboot[b] = (idx >= 0) ? muGrid[idx] : Double.NaN;
            }

            // Summarize the bootstrap μc distribution.
            double[] finiteBoot = Arrays.stream(muCboot)
                    .filter(Double::isFinite)
                    .sorted()
                    .toArray();

            double sd = stddev(finiteBoot);
            double lo = quantile(finiteBoot, 0.025);
            double hi = quantile(finiteBoot, 0.975);

            // Stop when μc is stable to within half a μ-bin.
            if (Double.isFinite(sd) && sd <= 0.5 * dMu) {
                return new Result(muC, sd, lo, hi, R);
            }

            // Or stop if we have reached the maximum allowed runs.
            if (R >= runsMax) {
                return new Result(muC, sd, lo, hi, R);
            }
        }

        // Fallback: should rarely happen unless the loop exits unexpectedly.
        return new Result(Double.NaN, Double.NaN, Double.NaN, Double.NaN, R);
    }

    /**
     * Compute a susceptibility-like curve across μ.
     *
     * <p>For each μ bucket, take the sample variance across runs of the
     * per-run mean coherence values. Larger variance indicates greater
     * transition sensitivity at that μ.</p>
     *
     * @param samplesByMu list of run-summary arrays, one array per μ
     * @return susceptibility proxy χ(μ)
     */
    private static double[] susceptibilityAcrossRuns(List<double[]> samplesByMu) {
        double[] chi = new double[samplesByMu.size()];

        for (int i = 0; i < samplesByMu.size(); i++) {
            double[] x = Arrays.stream(samplesByMu.get(i))
                    .filter(Double::isFinite)
                    .toArray();

            chi[i] = (x.length >= 2) ? sampleVariance(x) : Double.NaN;
        }

        return chi;
    }

    /**
     * Return the index of the largest finite entry in the array.
     *
     * @param a input array
     * @return index of maximal finite value, or -1 if none exist
     */
    private static int argmaxFinite(double[] a) {
        int bestI = -1;
        double best = -Double.MAX_VALUE;

        for (int i = 0; i < a.length; i++) {
            double v = a[i];
            if (Double.isFinite(v) && v > best) {
                best = v;
                bestI = i;
            }
        }
        return bestI;
    }

    /**
     * Sample variance with n-1 denominator.
     *
     * @param x sample values
     * @return unbiased sample variance, or NaN if fewer than 2 values
     */
    private static double sampleVariance(double[] x) {
        int n = x.length;
        if (n < 2) return Double.NaN;

        double m = 0.0;
        for (double v : x) m += v;
        m /= n;

        double s = 0.0;
        for (double v : x) {
            double d = v - m;
            s += d * d;
        }

        return s / (n - 1);
    }

    /**
     * Sample standard deviation with n-1 denominator.
     *
     * @param x sample values
     * @return sample standard deviation, or NaN if too few values
     */
    private static double stddev(double[] x) {
        if (x == null || x.length < 2) return Double.NaN;

        double m = 0.0;
        for (double v : x) m += v;
        m /= x.length;

        double s = 0.0;
        for (double v : x) {
            double d = v - m;
            s += d * d;
        }

        return Math.sqrt(s / (x.length - 1));
    }

    /**
     * Linear-interpolated quantile of a sorted array.
     *
     * @param sorted array already sorted in ascending order
     * @param q quantile in [0,1]
     * @return interpolated quantile value
     */
    private static double quantile(double[] sorted, double q) {
        if (sorted == null || sorted.length == 0) return Double.NaN;

        double pos = q * (sorted.length - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);

        if (lo == hi) return sorted[lo];

        double w = pos - lo;
        return (1 - w) * sorted[lo] + w * sorted[hi];
    }
}