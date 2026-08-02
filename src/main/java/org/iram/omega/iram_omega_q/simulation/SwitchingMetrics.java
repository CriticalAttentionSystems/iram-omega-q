/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;
import org.iram.omega.iram_omega_q.simulation.RegulationSimulation.SimulationResult;

/**
 *
 * @author veronique
 */
/** Post-burn-in summary quantities for one RF/DF switching trajectory. */
public final class SwitchingMetrics {

    private SwitchingMetrics() { }

    /**
     * Summary statistics used in the switching study.
     *
     * <p>A_mu and A_deltaC are defined transparently as post-burn-in
     * peak-to-peak amplitudes: max(x) - min(x).  Susceptibility is the temporal
     * population variance of the coherence gap, matching the existing paper
     * convention.</p>
     */
    public static final class Summary {
        public final double meanMu;
        public final double meanDeltaC;
        public final double susceptibility;
        public final double amplitudeMu;
        public final double amplitudeDeltaC;
        public final double fractionRF;
        public final int transitions;
        public final double transitionRate;
        public final double meanDFEpisodeTime;
        public final int samples;

        private Summary(double meanMu, double meanDeltaC, double susceptibility,
                        double amplitudeMu, double amplitudeDeltaC, double fractionRF,
                        int transitions, double transitionRate, double meanDFEpisodeTime,
                        int samples) {
            this.meanMu = meanMu;
            this.meanDeltaC = meanDeltaC;
            this.susceptibility = susceptibility;
            this.amplitudeMu = amplitudeMu;
            this.amplitudeDeltaC = amplitudeDeltaC;
            this.fractionRF = fractionRF;
            this.transitions = transitions;
            this.transitionRate = transitionRate;
            this.meanDFEpisodeTime = meanDFEpisodeTime;
            this.samples = samples;
        }
    }

    public static Summary summarize(SimulationResult r, int burnInSamples) {
        if (r == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        int nAll = Math.min(r.mu.size(), Math.min(r.coherence.size(), r.orderingRF.size()));
        if (burnInSamples < 0 || burnInSamples >= nAll) {
            throw new IllegalArgumentException(
                    "burnInSamples must be >= 0 and < recorded sample count; got "
                    + burnInSamples + " of " + nAll);
        }

        int n = nAll - burnInSamples;
        double sumMu = 0.0;
        double sumDC = 0.0;
        double minMu = Double.POSITIVE_INFINITY;
        double maxMu = Double.NEGATIVE_INFINITY;
        double minDC = Double.POSITIVE_INFINITY;
        double maxDC = Double.NEGATIVE_INFINITY;
        int rfCount = 0;
        int transitions = 0;

        double dt = estimateRecordedDt(r, burnInSamples);
        int dfEpisodeCount = 0;
        int currentDFLength = 0;
        int totalDFLength = 0;

        for (int i = burnInSamples; i < nAll; i++) {
            double mu = r.mu.get(i);
            double dc = r.coherence.get(i);
            int rf = r.orderingRF.get(i);
            sumMu += mu;
            sumDC += dc;
            minMu = Math.min(minMu, mu);
            maxMu = Math.max(maxMu, mu);
            minDC = Math.min(minDC, dc);
            maxDC = Math.max(maxDC, dc);
            rfCount += rf;

            if (i > burnInSamples && r.orderingRF.get(i) != r.orderingRF.get(i - 1)) {
                transitions++;
            }

            if (rf == 0) {
                currentDFLength++;
            } else if (currentDFLength > 0) {
                totalDFLength += currentDFLength;
                dfEpisodeCount++;
                currentDFLength = 0;
            }
        }
        if (currentDFLength > 0) {
            totalDFLength += currentDFLength;
            dfEpisodeCount++;
        }

        double meanMu = sumMu / n;
        double meanDC = sumDC / n;
        double varDC = 0.0;
        for (int i = burnInSamples; i < nAll; i++) {
            double diff = r.coherence.get(i) - meanDC;
            varDC += diff * diff;
        }
        varDC /= n;

        double observedTime = Math.max(dt, n * dt);
        double meanDFEpisodeTime = dfEpisodeCount == 0
                ? 0.0 : ((double) totalDFLength / dfEpisodeCount) * dt;

        return new Summary(
                meanMu,
                meanDC,
                varDC,
                maxMu - minMu,
                maxDC - minDC,
                (double) rfCount / n,
                transitions,
                transitions / observedTime,
                meanDFEpisodeTime,
                n
        );
    }

    private static double estimateRecordedDt(SimulationResult r, int start) {
        if (r.time.size() > start + 1) {
            return r.time.get(start + 1) - r.time.get(start);
        }
        return 1.0;
    }
}
