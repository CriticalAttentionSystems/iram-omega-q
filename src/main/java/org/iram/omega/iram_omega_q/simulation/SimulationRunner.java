/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.*;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.iram.omega.iram_omega_q.simulation.ConsciousSimulation.SimulationResult;

/**
 *
 * @author veronique
 */
public final class SimulationRunner {

    private SimulationRunner() {}

    public static void run(RunConfig cfg) throws Exception {
        if (cfg == null) {
            throw new IllegalArgumentException("cfg must not be null");
        }

        /*
         * HYSTERESIS is defined as a continuous up-and-down protocol. Store
         * the effective schedule in the saved configuration and manifest.
         */
        if (cfg.mode == RunConfig.Mode.HYSTERESIS) {
            cfg.targetEntropySchedule = SimulationParameters.ScheduleType.TRIANGULAR;
        }

        Path out = Path.of(cfg.outDir, cfg.runName);
        Files.createDirectories(out);

        // Always save the exact effective config next to results
        cfg.save(out.resolve("run.properties"));

        // Map cfg -> SimulationParameters
        SimulationParameters p = cfg.toSimulationParameters();

        switch (cfg.mode) {
            case SINGLE -> runSingle(p, out);
            case AVG -> runAvg(p, cfg, out);
            case SWEEP_EXPLORATORY -> runSweepExploratory(p, cfg, out);
            case SWEEP_PUBLICATION -> runSweepPublication(p, cfg, out);
            case HYSTERESIS -> runHysteresis(p, cfg, out);
            case TARGET_ENTROPY_SWEEP -> runTargetEntropySweep(p, cfg, out);
            case DWELL_TIME -> runDwellTime(p, cfg, out);
            case AMNESIA -> runAmnesia(p, cfg, out);
            case PERTURBATION -> runPerturbation(p, cfg, out);
        }

        writeManifest(out, cfg, p);
    }

    private static void runSingle(SimulationParameters p, Path out) throws IOException {
        SimulationResult r = ConsciousSimulation.run(p);

        Csv.writeSeries(out.resolve("entropy.csv"), r.time, r.entropy, "t", "SvN");
        Csv.writeSeries(out.resolve("coherence.csv"), r.time, r.coherence, "t", "dC");
        Csv.writeSeries(out.resolve("mu.csv"), r.time, r.mu, "t", "mu");
        Csv.writeSeries(out.resolve("dmu.csv"), r.time, r.deltaMu, "t", "dmu");

        Csv.writeSeries(out.resolve("targetEntropy.csv"), r.time, r.targetEntropy, "t", "targetEntropy");
        Csv.writeSeries(out.resolve("effectiveNoise.csv"), r.time, r.effectiveNoise, "t", "effectiveNoise");

        Csv.writeSeries(out.resolve("interventionFlag.csv"), r.time, toDoubleList(r.interventionFlag), "t", "interventionFlag");
        Csv.writeSeries(out.resolve("resetFlag.csv"), r.time, toDoubleList(r.resetFlag), "t", "resetFlag");
        
        double settlingTime = SettlingAnalyzer.detectSettlingTime(r, p.settleEps, p.settleWindow);
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"settlingTime", Double.toString(settlingTime)});
        rows.add(new String[]{"settledFraction", Double.toString(SettlingAnalyzer.settledFraction(r, p.settleEps))});

        Csv.writeRows(
                out.resolve("settling_summary.csv"),
                new String[]{"metric", "value"},
                rows
        );
    }

    private static void runAvg(SimulationParameters p, RunConfig cfg, Path out) throws IOException {
        int runs = cfg.avgRuns;
        int burnInSamples = cfg.avgBurnInSamples;

        if (burnInSamples < 0) {
            throw new IllegalArgumentException("avgBurnInSamples must be >= 0, got " + burnInSamples);
        }

        AveragedResult r = ConsciousSimulation.runAveraged(p, runs, burnInSamples);

        Csv.writeMeanStd(out.resolve("entropy_ci.csv"), r.time, r.meanEntropy, r.stdEntropy, "t", "SvN");
        Csv.writeMeanStd(out.resolve("coherence_ci.csv"), r.time, r.meanCoherence, r.stdCoherence, "t", "dC");
        Csv.writeMeanStd(out.resolve("mu_ci.csv"), r.time, r.meanMu, r.stdMu, "t", "mu");
        Csv.writeMeanStd(out.resolve("dmu_ci.csv"), r.time, r.meanDeltaMu, r.stdDeltaMu, "t", "dmu");
    }

    private static void runSweepExploratory(SimulationParameters base, RunConfig cfg, Path out) throws IOException {
        boolean fast = cfg.fast;

        double[] muGrid = Grid.parse(cfg.muGrid);
        double[] noiseGrid = Grid.parse(cfg.noiseGrid);

        int reps = fast ? 5 : 15;

        double[][] heatmap = new double[muGrid.length][noiseGrid.length]; // [mu][noise]

        for (int i = 0; i < muGrid.length; i++) {
            for (int j = 0; j < noiseGrid.length; j++) {
                double sum = 0.0;

                for (int r = 0; r < reps; r++) {
                    SimulationParameters p = base.copy();
                    p.muInit = muGrid[i];
                    p.emotionalNoise = noiseGrid[j];
                    p.seed = Util.mixSeed(p.baseSeed, i, j, r);
                    
                    SimulationResult rs = ConsciousSimulation.run(p, ConsciousSimulation.Mode.EXPLORATORY);
                    sum += rs.coherence.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                }

                heatmap[i][j] = sum / reps;
            }
        }

        Csv.writeGrid(out.resolve("mu_grid.csv"), muGrid);
        Csv.writeGrid(out.resolve("noise_grid.csv"), noiseGrid);
        Csv.writeMatrix(out.resolve("heatmap_meanCoherence.csv"), heatmap);
    }

    private static void runSweepPublication(SimulationParameters base, RunConfig cfg, Path out) throws IOException {
        boolean fast = cfg.fast;

        double[] muGrid = Grid.parse(cfg.muGrid);
        double[] noiseGrid = Grid.parse(cfg.noiseGrid);

        SimulationParameters sweepParams = base.copy();

        int burnIn = cfg.burnIn;
        int runsPerPoint = cfg.runsPerPoint;

        if (fast) {
            sweepParams.steps = 4000;
            burnIn = 400;
            runsPerPoint = 5;
        }

        PhaseDiagramResult result = PhaseDiagramSweep.run(
                sweepParams,
                noiseGrid,
                muGrid,
                burnIn,
                runsPerPoint,
                cfg.threads
        );

        double[][] meanC = ensureMuNoiseShape(result.meanCoherence, result.muValues.length, result.noiseValues.length);
        double[][] chi   = ensureMuNoiseShape(result.susceptibility, result.muValues.length, result.noiseValues.length);

        double[] muCritical = PhaseDiagramSweep.detectCriticalMu(result.muValues, chi);

        Csv.writeGrid(out.resolve("mu_grid.csv"), result.muValues);
        Csv.writeGrid(out.resolve("noise_grid.csv"), result.noiseValues);
        Csv.writeMatrix(out.resolve("meanCoherence.csv"), meanC);
        Csv.writeMatrix(out.resolve("susceptibility.csv"), chi);
        Csv.writeGrid(out.resolve("muCritical.csv"), muCritical);
    }

    private static void runTargetEntropySweep(SimulationParameters p, RunConfig cfg, Path out) throws IOException {
        SimulationResult r = ConsciousSimulation.run(p, ConsciousSimulation.Mode.PUBLICATION);

        Csv.writeSeries(out.resolve("entropy.csv"), r.time, r.entropy, "t", "SvN");
        Csv.writeSeries(out.resolve("coherence.csv"), r.time, r.coherence, "t", "dC");
        Csv.writeSeries(out.resolve("mu.csv"), r.time, r.mu, "t", "mu");
        Csv.writeSeries(out.resolve("targetEntropy.csv"), r.time, r.targetEntropy, "t", "targetEntropy");
        Csv.writeSeries(out.resolve("effectiveNoise.csv"), r.time, r.effectiveNoise, "t", "effectiveNoise");
        Csv.writeSeries(out.resolve("interventionFlag.csv"), r.time, toDoubleList(r.interventionFlag), "t", "interventionFlag");
        Csv.writeSeries(out.resolve("resetFlag.csv"), r.time, toDoubleList(r.resetFlag), "t", "resetFlag");
    }

    private static void runAmnesia(SimulationParameters p, RunConfig cfg, Path out) throws IOException {
        SimulationResult r = ConsciousSimulation.run(p, ConsciousSimulation.Mode.PUBLICATION);

        Csv.writeSeries(out.resolve("entropy.csv"), r.time, r.entropy, "t", "SvN");
        Csv.writeSeries(out.resolve("coherence.csv"), r.time, r.coherence, "t", "dC");
        Csv.writeSeries(out.resolve("mu.csv"), r.time, r.mu, "t", "mu");
        Csv.writeSeries(out.resolve("resetFlag.csv"), r.time, toDoubleList(r.resetFlag), "t", "resetFlag");
        Csv.writeSeries(out.resolve("effectiveNoise.csv"), r.time, r.effectiveNoise, "t", "effectiveNoise");
        
        double settlingTime = SettlingAnalyzer.detectSettlingTime(r, p.settleEps, p.settleWindow);
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"settlingTime", Double.toString(settlingTime)});
        rows.add(new String[]{"settledFraction", Double.toString(SettlingAnalyzer.settledFraction(r, p.settleEps))});

        Csv.writeRows(
                out.resolve("settling_summary.csv"),
                new String[]{"metric", "value"},
                rows
        );
    }

    private static void runPerturbation(SimulationParameters p, RunConfig cfg, Path out) throws IOException {
        /*
         * Use PUBLICATION mode so only the explicitly configured intervention
         * is present. EXPLORATORY mode adds periodic noise bursts and would
         * confound a paper-level recovery analysis.
         */
        SimulationResult r = ConsciousSimulation.run(p, ConsciousSimulation.Mode.PUBLICATION);

        Csv.writeSeries(out.resolve("entropy.csv"), r.time, r.entropy, "t", "SvN");
        Csv.writeSeries(out.resolve("coherence.csv"), r.time, r.coherence, "t", "dC");
        Csv.writeSeries(out.resolve("mu.csv"), r.time, r.mu, "t", "mu");
        Csv.writeSeries(out.resolve("effectiveNoise.csv"), r.time, r.effectiveNoise, "t", "effectiveNoise");
        Csv.writeSeries(out.resolve("interventionFlag.csv"), r.time, toDoubleList(r.interventionFlag), "t", "interventionFlag");
        
        double settlingTime = SettlingAnalyzer.detectSettlingTime(r, p.settleEps, p.settleWindow);
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"settlingTime", Double.toString(settlingTime)});
        rows.add(new String[]{"settledFraction", Double.toString(SettlingAnalyzer.settledFraction(r, p.settleEps))});

        Csv.writeRows(
                out.resolve("settling_summary.csv"),
                new String[]{"metric", "value"},
                rows
        );
    }

    private static void runDwellTime(SimulationParameters p, RunConfig cfg, Path out) throws IOException {
        SimulationResult r = ConsciousSimulation.run(p, ConsciousSimulation.Mode.PUBLICATION);

        Csv.writeSeries(out.resolve("coherence.csv"), r.time, r.coherence, "t", "dC");
        Csv.writeSeries(out.resolve("mu.csv"), r.time, r.mu, "t", "mu");
        Csv.writeSeries(out.resolve("effectiveNoise.csv"), r.time, r.effectiveNoise, "t", "effectiveNoise");

        List<Integer> labels = DwellTimeAnalyzer.labelRegimes(
                r,
                p.focusThresholdLow,
                p.focusThresholdHigh
        );

        List<Double> labelAsDouble = toDoubleList(labels);
        Csv.writeSeries(out.resolve("regimeLabel.csv"), r.time, labelAsDouble, "t", "label");

        List<Double> lowDwells = DwellTimeAnalyzer.computeDwellTimes(r, labels, DwellTimeAnalyzer.LOW);
        List<Double> midDwells = DwellTimeAnalyzer.computeDwellTimes(r, labels, DwellTimeAnalyzer.MID);
        List<Double> highDwells = DwellTimeAnalyzer.computeDwellTimes(r, labels, DwellTimeAnalyzer.HIGH);

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"LOW", Integer.toString(lowDwells.size()), Double.toString(DwellTimeAnalyzer.mean(lowDwells))});
        rows.add(new String[]{"MID", Integer.toString(midDwells.size()), Double.toString(DwellTimeAnalyzer.mean(midDwells))});
        rows.add(new String[]{"HIGH", Integer.toString(highDwells.size()), Double.toString(DwellTimeAnalyzer.mean(highDwells))});
        
        
        Csv.writeRows(
                out.resolve("dwell_summary.csv"),
                new String[]{"regime", "count", "meanDwellTime"},
                rows
        );

        double tr = DwellTimeAnalyzer.transitionRate(r, labels);
        List<String[]> trRows = new ArrayList<>();
        trRows.add(new String[]{"transitionRate", Double.toString(tr)});
        Csv.writeRows(out.resolve("transition_rate.csv"), new String[]{"metric", "value"}, trRows);
        
        List<String[]> dwellRows = new ArrayList<>();

        for (double x : lowDwells) {
            dwellRows.add(new String[]{"LOW", Double.toString(x)});
        }
        for (double x : midDwells) {
            dwellRows.add(new String[]{"MID", Double.toString(x)});
        }
        for (double x : highDwells) {
            dwellRows.add(new String[]{"HIGH", Double.toString(x)});
        }

        Csv.writeRows(
                out.resolve("dwell_times.csv"),
                new String[]{"regime", "dwellTime"},
                dwellRows
        );
    }
    
    /**
     * Run a genuine hysteresis protocol as one continuous trajectory.
     *
     * Earlier versions ran the ascending and descending ramps as two
     * independent simulations initialized from the same starting condition.
     * That tests ramp direction but not hysteresis/carryover.  Here the target
     * follows a triangular schedule in one run, so state and controller values
     * at the turning point are inherited by the descending branch.
     */
    private static void runHysteresis(SimulationParameters p, RunConfig cfg, Path out) throws IOException {
        if (!cfg.sweepUp || !cfg.sweepDown) {
            throw new IllegalArgumentException(
                    "HYSTERESIS mode requires sweepUp=true and sweepDown=true "
                    + "because a loop must contain both continuous branches.");
        }
        if (!Double.isFinite(cfg.targetEntropyStart) ||
                !Double.isFinite(cfg.targetEntropyEnd)) {
            throw new IllegalArgumentException(
                    "HYSTERESIS mode requires finite targetEntropyStart and targetEntropyEnd.");
        }

        SimulationParameters loop = p.copy();
        loop.targetEntropyStart = cfg.targetEntropyStart;
        loop.targetEntropyEnd = cfg.targetEntropyEnd;
        loop.targetEntropySchedule = SimulationParameters.ScheduleType.TRIANGULAR;

        SimulationResult r = ConsciousSimulation.run(loop, ConsciousSimulation.Mode.PUBLICATION);

        Csv.writeSeries(out.resolve("hysteresis_entropy.csv"), r.time, r.entropy, "t", "SvN");
        Csv.writeSeries(out.resolve("hysteresis_coherence.csv"), r.time, r.coherence, "t", "dC");
        Csv.writeSeries(out.resolve("hysteresis_mu.csv"), r.time, r.mu, "t", "mu");
        Csv.writeSeries(out.resolve("hysteresis_targetEntropy.csv"), r.time, r.targetEntropy, "t", "targetEntropy");
        Csv.writeSeries(out.resolve("hysteresis_effectiveNoise.csv"), r.time, r.effectiveNoise, "t", "effectiveNoise");

        List<String[]> allRows = new ArrayList<>();
        List<String[]> upRows = new ArrayList<>();
        List<String[]> downRows = new ArrayList<>();
        double turnTime = 0.5 * (loop.steps - 1) * loop.dt;

        for (int i = 0; i < r.time.size(); i++) {
            String direction = r.time.get(i) <= turnTime ? "up" : "down";
            String[] row = new String[] {
                direction,
                Double.toString(r.time.get(i)),
                Double.toString(r.targetEntropy.get(i)),
                Double.toString(r.entropy.get(i)),
                Double.toString(r.coherence.get(i)),
                Double.toString(r.mu.get(i)),
                Double.toString(r.effectiveNoise.get(i))
            };
            allRows.add(row);
            if ("up".equals(direction)) {
                upRows.add(row);
            } else {
                downRows.add(row);
            }
        }

        String[] header = {
            "direction", "t", "targetEntropy", "SvN", "dC", "mu", "effectiveNoise"
        };
        Csv.writeRows(out.resolve("hysteresis_loop.csv"), header, allRows);
        Csv.writeRows(out.resolve("hysteresis_up_branch.csv"), header, upRows);
        Csv.writeRows(out.resolve("hysteresis_down_branch.csv"), header, downRows);
    }

    private static double[][] ensureMuNoiseShape(double[][] a, int muLen, int noiseLen) {
        if (a == null || a.length == 0 || a[0].length == 0) {
            return a;
        }

        int r = a.length;
        int c = a[0].length;

        if (r == muLen && c == noiseLen) {
            return a;
        }

        if (r == noiseLen && c == muLen) {
            double[][] t = new double[muLen][noiseLen];
            for (int i = 0; i < r; i++) {
                for (int j = 0; j < c; j++) {
                    t[j][i] = a[i][j];
                }
            }
            return t;
        }

        return a;
    }

    private static List<Double> toDoubleList(List<Integer> xs) {
        List<Double> out = new ArrayList<>(xs.size());
        for (Integer x : xs) {
            out.add(x == null ? Double.NaN : x.doubleValue());
        }
        return out;
    }

    private static void writeManifest(Path out, RunConfig cfg, SimulationParameters p) throws IOException {
        Path f = out.resolve("manifest.txt");
        try (BufferedWriter w = Files.newBufferedWriter(f)) {
            w.write("time=" + ZonedDateTime.now() + "\n");
            w.write("mode=" + cfg.mode + "\n");
            w.write("fast=" + cfg.fast + "\n");

            w.write("mu=" + cfg.mu + "\n");
            w.write("noise=" + cfg.noise + "\n");
            w.write("targetEntropy=" + cfg.targetEntropy + "\n");

            w.write("steps=" + cfg.steps + "\n");
            w.write("seed=" + cfg.seed + "\n");
            w.write("baseSeed=" + cfg.baseSeed + "\n");
            w.write("ordering=" + cfg.ordering + "\n");

            w.write("dt=" + cfg.dt + "\n");
            w.write("dim=" + cfg.dim + "\n");
            w.write("salienceCenter=" + cfg.salienceCenter + "\n");
            w.write("salienceWidth=" + cfg.salienceWidth + "\n");
            w.write("phaseNoise=" + cfg.phaseNoise + "\n");
            w.write("focusIndex=" + cfg.focusIndex + "\n");
            w.write("energyScale=" + cfg.energyScale + "\n");
            w.write("coupling=" + cfg.coupling + "\n");
            w.write("locality=" + cfg.locality + "\n");
            w.write("muTargetGain=" + cfg.muTargetGain + "\n");
            w.write("muDerivativeGain=" + cfg.muDerivativeGain + "\n");
            
            w.write("muMin=" + cfg.muMin + "\n");
            w.write("muMax=" + cfg.muMax + "\n");

            w.write("avgRuns=" + cfg.avgRuns + "\n");
            w.write("avgBurnInSamples=" + cfg.avgBurnInSamples + "\n");

            w.write("muGrid=" + cfg.muGrid + "\n");
            w.write("noiseGrid=" + cfg.noiseGrid + "\n");
            w.write("burnIn=" + cfg.burnIn + "\n");
            w.write("runsPerPoint=" + cfg.runsPerPoint + "\n");

            w.write("settleEps=" + cfg.settleEps + "\n");
            w.write("settleWindow=" + cfg.settleWindow + "\n");
            w.write("interventionStart=" + cfg.interventionStart + "\n");
            w.write("interventionEnd=" + cfg.interventionEnd + "\n");
            w.write("muLearningScaleDuringIntervention=" + cfg.muLearningScaleDuringIntervention + "\n");
            w.write("noiseMultiplierDuringIntervention=" + cfg.noiseMultiplierDuringIntervention + "\n");
            w.write("targetEntropyDuringIntervention=" + cfg.targetEntropyDuringIntervention + "\n");
            w.write("resetStateAtStep=" + cfg.resetStateAtStep + "\n");
            w.write("resetMuAtStep=" + cfg.resetMuAtStep + "\n");
            w.write("keepControllerHistoryOnReset=" + cfg.keepControllerHistoryOnReset + "\n");
            w.write("resetToInitialState=" + cfg.resetToInitialState + "\n");
            w.write("targetEntropyStart=" + cfg.targetEntropyStart + "\n");
            w.write("targetEntropyEnd=" + cfg.targetEntropyEnd + "\n");
            w.write("targetEntropySchedule=" + cfg.targetEntropySchedule + "\n");
            w.write("focusThresholdLow=" + cfg.focusThresholdLow + "\n");
            w.write("focusThresholdHigh=" + cfg.focusThresholdHigh + "\n");
            w.write("sweepUp=" + cfg.sweepUp + "\n");
            w.write("sweepDown=" + cfg.sweepDown + "\n");
            w.write("experimentTag=" + cfg.experimentTag + "\n");
            w.write("threads=" + cfg.threads + "\n");
        }
    }
}