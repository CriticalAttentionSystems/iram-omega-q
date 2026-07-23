/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author veronique
 */
public class PhaseDiagramSweep {

    private record PointResult(
            int muIndex,
            int noiseIndex,
            double meanCoherence,
            double susceptibility
    ) {}

    public static PhaseDiagramResult run(
            SimulationParameters base,
            double[] noiseGrid,
            double[] muGrid,
            int burnIn,
            int runsPerPoint
    ) {
        return run(base, noiseGrid, muGrid, burnIn, runsPerPoint, 1);
    }

    public static PhaseDiagramResult run(
            SimulationParameters base,
            double[] noiseGrid,
            double[] muGrid,
            int burnIn,
            int runsPerPoint,
            int threads
    ) {
        long startNanos = System.nanoTime();
        AtomicInteger completed = new AtomicInteger(0);
        int total = muGrid.length * noiseGrid.length;

        threads = Math.max(1, threads);

        double[][] coherence = new double[muGrid.length][noiseGrid.length];
        double[][] susceptibility = new double[muGrid.length][noiseGrid.length];

        if (threads == 1) {
            for (int i = 0; i < muGrid.length; i++) {
                for (int j = 0; j < noiseGrid.length; j++) {
                    PointResult pr = computePoint(
                            base, noiseGrid, muGrid, burnIn, runsPerPoint, i, j
                    );
                    coherence[i][j] = pr.meanCoherence();
                    susceptibility[i][j] = pr.susceptibility();
                }
            }
        } else {
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            List<Future<PointResult>> futures = new ArrayList<>();

            try {
                for (int i = 0; i < muGrid.length; i++) {
                    for (int j = 0; j < noiseGrid.length; j++) {
                        final int muIndex = i;
                        final int noiseIndex = j;

                        Callable<PointResult> task = () -> {
                            PointResult pr = computePoint(
                                    base, noiseGrid, muGrid, burnIn, runsPerPoint,
                                    muIndex, noiseIndex
                            );

                            int k = completed.incrementAndGet();
                            if (k % 25 == 0 || k == total) {
                                double elapsedSec = (System.nanoTime() - startNanos) / 1.0e9;
                                double frac = (double) k / total;
                                double etaSec = frac > 0.0
                                        ? elapsedSec * (1.0 - frac) / frac
                                        : Double.NaN;

                                System.out.printf(
                                        "Sweep completed: %d/%d grid points (%.1f%%), elapsed %.1f min, ETA %.1f min%n",
                                        k, total, 100.0 * frac, elapsedSec / 60.0, etaSec / 60.0
                                );
                            }

                            return pr;
                        };

                        futures.add(pool.submit(task));
                    }
                }

                List<PointResult> results = new ArrayList<>(futures.size());

                for (Future<PointResult> f : futures) {
                    try {
                        results.add(f.get());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Sweep interrupted", e);
                    } catch (ExecutionException e) {
                        throw new RuntimeException("Sweep task failed", e.getCause());
                    }
                }

                results.sort(
                        Comparator.comparingInt(PointResult::muIndex)
                                .thenComparingInt(PointResult::noiseIndex)
                );

                for (PointResult pr : results) {
                    coherence[pr.muIndex()][pr.noiseIndex()] = pr.meanCoherence();
                    susceptibility[pr.muIndex()][pr.noiseIndex()] = pr.susceptibility();
                }

            } finally {
                pool.shutdown();

                try {
                    if (!pool.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)) {
                        pool.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    pool.shutdownNow();
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while shutting down sweep thread pool", e);
                }
            }
        }

        return new PhaseDiagramResult(
                noiseGrid,
                muGrid,
                coherence,
                susceptibility
        );
    }

    private static PointResult computePoint(
            SimulationParameters base,
            double[] noiseGrid,
            double[] muGrid,
            int burnIn,
            int runsPerPoint,
            int i,
            int j
    ) {
        double[] runMeans = new double[runsPerPoint];
        double[] runTemporalVariances = new double[runsPerPoint];
        int runCount = 0;

        for (int r = 0; r < runsPerPoint; r++) {
            SimulationParameters p = base.copy();

            p.emotionalNoise = noiseGrid[j];
            p.muInit = muGrid[i];

            /*
             * The seed depends on the grid cell and replicate, but not on
             * control ordering.  RF and DF sweeps therefore use matched
             * disturbance histories when run from otherwise identical configs.
             */
            p.seed = Util.mixSeed(
                    p.baseSeed,
                    i,
                    j,
                    r
            );

            RegulationSimulation.SweepStatistics statistics =
                    RegulationSimulation.runSweepStatistics(p, burnIn);

            if (Double.isFinite(statistics.meanCoherence) &&
                    Double.isFinite(statistics.temporalVariance)) {
                runMeans[runCount] = statistics.meanCoherence;
                runTemporalVariances[runCount] = statistics.temporalVariance;
                runCount++;
            }
        }

        if (runCount == 0) {
            return new PointResult(i, j, Double.NaN, Double.NaN);
        }

        double meanCoherence = 0.0;
        double meanTemporalVariance = 0.0;

        for (int r = 0; r < runCount; r++) {
            meanCoherence += runMeans[r];
            meanTemporalVariance += runTemporalVariances[r];
        }

        meanCoherence /= runCount;

        /*
         * Paper 1 susceptibility definition:
         *
         *     chi(mu, eta) = < Var_t[Delta C(t)] >_runs
         *
         * The previous implementation instead measured variance across run
         * means, which is seed-to-seed variability rather than dynamical
         * susceptibility.
         */
        double chi = meanTemporalVariance / runCount;

        return new PointResult(i, j, meanCoherence, chi);
    }

    public static double[] detectCriticalMu(
            double[] muGrid,
            double[][] susceptibility
    ) {
        if (muGrid == null || susceptibility == null ||
                susceptibility.length == 0 || susceptibility[0].length == 0) {
            throw new IllegalArgumentException("Empty μ grid or susceptibility matrix");
        }

        final int r = susceptibility.length;
        final int c = susceptibility[0].length;

        final boolean muIsRows;
        final int nMu;
        final int nNoise;

        if (r == muGrid.length) {
            muIsRows = true;
            nMu = r;
            nNoise = c;
        } else if (c == muGrid.length) {
            muIsRows = false;
            nMu = c;
            nNoise = r;
        } else {
            throw new IllegalArgumentException(
                    "Cannot match μ grid (len=" + muGrid.length +
                            ") to susceptibility dims " + r + "x" + c
            );
        }

        double[] muCritical = new double[nNoise];

        for (int j = 0; j < nNoise; j++) {
            int bestI = -1;
            double best = -Double.MAX_VALUE;

            for (int i = 0; i < nMu; i++) {
                double val = muIsRows
                        ? susceptibility[i][j]
                        : susceptibility[j][i];

                if (Double.isFinite(val) && val > best) {
                    best = val;
                    bestI = i;
                }
            }

            muCritical[j] = (bestI >= 0) ? muGrid[bestI] : Double.NaN;
        }

        return muCritical;
    }
}