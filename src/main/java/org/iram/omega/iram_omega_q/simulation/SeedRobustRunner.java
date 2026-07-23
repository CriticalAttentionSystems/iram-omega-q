/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;

import org.iram.omega.iram_omega_q.cognition.QuantumRegulationAgent;

/**
 *
 * @author veronique
 */
public class SeedRobustRunner {
    
    public final class SeedRobustnessRunner {

        public static SeedRobustnessResult runMuCSeedRobustness(
                SimulationParameters base,
                double[] noiseGrid,
                double[] muGrid,
                int burnIn,
                int runsPerPoint,
                QuantumRegulationAgent.ControlOrdering ordering,
                long seed0,
                int nSeeds
        ) {
            double[][] muC_bySeed = new double[nSeeds][noiseGrid.length];

            for (int s = 0; s < nSeeds; s++) {
                SimulationParameters p = base.copy();
                p.ordering = ordering;
                p.seed = seed0 + s;

                PhaseDiagramResult r = PhaseDiagramSweep.run(p, noiseGrid, muGrid, burnIn, runsPerPoint);

                double[] muC = PhaseDiagramSweep.detectCriticalMu(r.muValues, r.susceptibility);
                for (int j = 0; j < noiseGrid.length; j++) {
                    muC_bySeed[s][j] = (muC != null && j < muC.length) ? muC[j] : Double.NaN;
                }
            }

            return SeedRobustnessResult.from(muC_bySeed, noiseGrid);
        }
    }
    
    public record SeedRobustnessResult(
        double[] eta,
        double[] muC_mean,
        double[] muC_std,
        double[][] muC_bySeed
    ) {
        public static SeedRobustnessResult from(double[][] muC_bySeed, double[] eta) {
            int S = muC_bySeed.length;
            int M = eta.length;

            double[] mean = new double[M];
            double[] std  = new double[M];

            for (int j = 0; j < M; j++) {
                double sum = 0; int n = 0;
                for (int s = 0; s < S; s++) {
                    double v = muC_bySeed[s][j];
                    if (Double.isFinite(v)) { sum += v; n++; }
                }
                mean[j] = (n == 0) ? Double.NaN : (sum / n);

                double ss = 0; int n2 = 0;
                for (int s = 0; s < S; s++) {
                    double v = muC_bySeed[s][j];
                    if (Double.isFinite(v) && Double.isFinite(mean[j])) {
                        ss += (v - mean[j]) * (v - mean[j]);
                        n2++;
                    }
                }
                std[j] = (n2 <= 1) ? Double.NaN : Math.sqrt(ss / (n2 - 1));
            }

            return new SeedRobustnessResult(eta, mean, std, muC_bySeed);
        }
    }
    
}
