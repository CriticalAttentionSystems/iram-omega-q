/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.iram.omega.iram_omega_q.cognition.QuantumRegulationAgent.ControlOrdering;
import org.iram.omega.iram_omega_q.simulation.RegulationSimulation.SimulationResult;

/**
 *
 * @author veronique
 */
/**
 * Paper-level RF/DF switching experiment.
 *
 * <p>For every replicate, the runner first computes pure RF and pure DF
 * reference trajectories using the same environmental-noise seed.  It then
 * runs scheduled trajectories and computes the nonlinear switching penalty</p>
 *
 * <pre>
 * C_switch = meanMu_switch
 *          - [p_RF meanMu_RF + (1 - p_RF) meanMu_DF].
 * </pre>
 *
 * <p>Thus a positive penalty cannot be attributed merely to spending some
 * fraction of time in DF; it indicates additional regulatory burden associated
 * with switching/history.</p>
 */
/**
 * Paper-level RF/DF switching experiment.
 *
 * <p>For every replicate, the runner first computes pure RF and pure DF
 * reference trajectories using the same environmental-noise seed.  It then
 * runs scheduled trajectories and computes the nonlinear switching penalty</p>
 *
 * <pre>
 * C_switch = meanMu_switch
 *          - [p_RF meanMu_RF + (1 - p_RF) meanMu_DF].
 * </pre>
 *
 * <p>Thus a positive penalty cannot be attributed merely to spending some
 * fraction of time in DF; it indicates additional regulatory burden associated
 * with switching/history.</p>
 */
public final class SwitchingStudyRunner {

    private SwitchingStudyRunner() { }

    private static final String[] RUN_HEADER = {
        "protocol", "condition", "replicate", "periodicDwellSteps",
        "pLoss", "pReturn", "fractionRF", "meanDFEpisodeTime",
        "transitions", "transitionRate", "meanMu", "meanDeltaC",
        "chiDeltaC", "A_mu", "A_deltaC", "expectedMuMixture",
        "expectedDeltaCMixture", "C_switch_mu", "C_switch_deltaC"
    };

    private static final class Row {
        String protocol;
        String condition;
        int replicate;
        int dwell;
        double pLoss;
        double pReturn;
        SwitchingMetrics.Summary metrics;
        double expectedMu;
        double expectedDC;
        double penaltyMu;
        double penaltyDC;

        String[] asCsv() {
            return new String[] {
                protocol, condition, Integer.toString(replicate),
                dwell < 0 ? "" : Integer.toString(dwell),
                finiteOrBlank(pLoss), finiteOrBlank(pReturn),
                Double.toString(metrics.fractionRF),
                Double.toString(metrics.meanDFEpisodeTime),
                Integer.toString(metrics.transitions),
                Double.toString(metrics.transitionRate),
                Double.toString(metrics.meanMu),
                Double.toString(metrics.meanDeltaC),
                Double.toString(metrics.susceptibility),
                Double.toString(metrics.amplitudeMu),
                Double.toString(metrics.amplitudeDeltaC),
                Double.toString(expectedMu),
                Double.toString(expectedDC),
                Double.toString(penaltyMu),
                Double.toString(penaltyDC)
            };
        }
    }

    public static void run(SimulationParameters base, RunConfig cfg, Path out) throws IOException {
        validate(cfg);
        Files.createDirectories(out);
        Files.createDirectories(out.resolve("timeseries"));

        int[] dwellGrid = parseIntGrid(cfg.periodicDwellGrid);
        double[][] markovPairs = parsePairGrid(cfg.markovPairGrid);
        List<Row> allRows = new ArrayList<>();
        List<String[]> stationarityRows = new ArrayList<>();

        try (BufferedWriter eventWriter = Files.newBufferedWriter(out.resolve("switching_event_aligned.csv"))) {
            SwitchingDiagnostics.writeEventHeader(eventWriter);

        for (int rep = 0; rep < cfg.switchingRuns; rep++) {
            long trajectorySeed = Util.mixSeed(base.baseSeed, -1, -1, rep);

            SimulationParameters rfParams = baselineParams(base, trajectorySeed, ControlOrdering.REGULATION_FIRST);
            SimulationParameters dfParams = baselineParams(base, trajectorySeed, ControlOrdering.DISTURBANCE_FIRST);
            SimulationResult rfRun = RegulationSimulation.run(rfParams, RegulationSimulation.Mode.PUBLICATION);
            SimulationResult dfRun = RegulationSimulation.run(dfParams, RegulationSimulation.Mode.PUBLICATION);
            SwitchingMetrics.Summary rf = SwitchingMetrics.summarize(rfRun, cfg.switchingBurnInSamples);
            SwitchingMetrics.Summary df = SwitchingMetrics.summarize(dfRun, cfg.switchingBurnInSamples);

            allRows.add(referenceRow("FIXED_RF", rep, rf));
            allRows.add(referenceRow("FIXED_DF", rep, df));
            stationarityRows.add(SwitchingDiagnostics.stationarity(rfRun, cfg.switchingBurnInSamples)
                    .asCsv("FIXED_RF", "FIXED_RF", rep));
            stationarityRows.add(SwitchingDiagnostics.stationarity(dfRun, cfg.switchingBurnInSamples)
                    .asCsv("FIXED_DF", "FIXED_DF", rep));
            if (rep == 0) {
                writeTimeSeries(out.resolve("timeseries/fixed_rf.csv"), rfRun);
                writeTimeSeries(out.resolve("timeseries/fixed_df.csv"), dfRun);
            }

            for (int conditionIndex = 0; conditionIndex < dwellGrid.length; conditionIndex++) {
                int dwell = dwellGrid[conditionIndex];
                SimulationParameters p = base.copy();
                p.seed = trajectorySeed;
                p.ordering = ControlOrdering.REGULATION_FIRST;
                p.switchingProtocol = OrderingSchedule.Protocol.PERIODIC;
                p.periodicDwellSteps = dwell;
                p.switchingSeed = mixedSwitchSeed(cfg.switchingBaseSeed, 1, conditionIndex, rep);

                SimulationResult run = RegulationSimulation.run(p, RegulationSimulation.Mode.PUBLICATION);
                SwitchingMetrics.Summary sm = SwitchingMetrics.summarize(run, cfg.switchingBurnInSamples);
                Row row = switchingRow("PERIODIC", "L=" + dwell, rep, dwell,
                        Double.NaN, Double.NaN, sm, rf, df);
                allRows.add(row);
                stationarityRows.add(SwitchingDiagnostics.stationarity(run, cfg.switchingBurnInSamples)
                        .asCsv("PERIODIC", "L=" + dwell, rep));
                SwitchingDiagnostics.writeEventAligned(eventWriter, "PERIODIC", "L=" + dwell, rep,
                        run, rfRun, dfRun, cfg.switchingBurnInSamples,
                        cfg.switchingDiagnosticMaxLagSteps, cfg.switchingDiagnosticStrideSteps);
                if (rep == 0) {
                    writeTimeSeries(out.resolve("timeseries/periodic_L" + dwell + ".csv"), run);
                }
            }

            for (int conditionIndex = 0; conditionIndex < markovPairs.length; conditionIndex++) {
                double pLoss = markovPairs[conditionIndex][0];
                double pReturn = markovPairs[conditionIndex][1];
                SimulationParameters p = base.copy();
                p.seed = trajectorySeed;
                p.ordering = ControlOrdering.REGULATION_FIRST;
                p.switchingProtocol = OrderingSchedule.Protocol.MARKOV;
                p.pLoss = pLoss;
                p.pReturn = pReturn;
                p.switchingSeed = mixedSwitchSeed(cfg.switchingBaseSeed, 2, conditionIndex, rep);

                SimulationResult run = RegulationSimulation.run(p, RegulationSimulation.Mode.PUBLICATION);
                SwitchingMetrics.Summary sm = SwitchingMetrics.summarize(run, cfg.switchingBurnInSamples);
                String cond = "pLoss=" + formatKey(pLoss) + "_pReturn=" + formatKey(pReturn);
                Row row = switchingRow("MARKOV", cond, rep, -1,
                        pLoss, pReturn, sm, rf, df);
                allRows.add(row);
                stationarityRows.add(SwitchingDiagnostics.stationarity(run, cfg.switchingBurnInSamples)
                        .asCsv("MARKOV", cond, rep));
                SwitchingDiagnostics.writeEventAligned(eventWriter, "MARKOV", cond, rep,
                        run, rfRun, dfRun, cfg.switchingBurnInSamples,
                        cfg.switchingDiagnosticMaxLagSteps, cfg.switchingDiagnosticStrideSteps);
                if (rep == 0) {
                    writeTimeSeries(out.resolve("timeseries/markov_" + cond + ".csv"), run);
                }
            }
        }
        }

        List<String[]> rawRows = new ArrayList<>();
        for (Row row : allRows) {
            rawRows.add(row.asCsv());
        }
        Csv.writeRows(out.resolve("switching_runs.csv"), RUN_HEADER, rawRows);
        Csv.writeRows(out.resolve("switching_summary.csv"), summaryHeader(), summarizeRows(allRows));
        Csv.writeRows(out.resolve("switching_stationarity_runs.csv"), SwitchingDiagnostics.stationarityHeader(), stationarityRows);
        writeMetricDefinitions(out.resolve("switching_metric_definitions.txt"), cfg);
    }

    private static SimulationParameters baselineParams(
            SimulationParameters base, long seed, ControlOrdering ordering) {
        SimulationParameters p = base.copy();
        p.seed = seed;
        p.ordering = ordering;
        p.switchingProtocol = OrderingSchedule.Protocol.FIXED;
        return p;
    }

    private static Row referenceRow(String protocol, int replicate, SwitchingMetrics.Summary sm) {
        Row row = new Row();
        row.protocol = protocol;
        row.condition = protocol;
        row.replicate = replicate;
        row.dwell = -1;
        row.pLoss = Double.NaN;
        row.pReturn = Double.NaN;
        row.metrics = sm;
        row.expectedMu = sm.meanMu;
        row.expectedDC = sm.meanDeltaC;
        row.penaltyMu = 0.0;
        row.penaltyDC = 0.0;
        return row;
    }

    private static Row switchingRow(
            String protocol, String condition, int replicate, int dwell,
            double pLoss, double pReturn, SwitchingMetrics.Summary sm,
            SwitchingMetrics.Summary rf, SwitchingMetrics.Summary df) {
        Row row = new Row();
        row.protocol = protocol;
        row.condition = condition;
        row.replicate = replicate;
        row.dwell = dwell;
        row.pLoss = pLoss;
        row.pReturn = pReturn;
        row.metrics = sm;
        row.expectedMu = sm.fractionRF * rf.meanMu + (1.0 - sm.fractionRF) * df.meanMu;
        row.expectedDC = sm.fractionRF * rf.meanDeltaC + (1.0 - sm.fractionRF) * df.meanDeltaC;
        row.penaltyMu = sm.meanMu - row.expectedMu;
        row.penaltyDC = sm.meanDeltaC - row.expectedDC;
        return row;
    }

    private static String[] summaryHeader() {
        return new String[] {
            "protocol", "condition", "periodicDwellSteps", "pLoss", "pReturn", "runs",
            "fractionRF_mean", "fractionRF_std", "meanDFEpisodeTime_mean", "meanDFEpisodeTime_std",
            "transitionRate_mean", "transitionRate_std", "meanMu_mean", "meanMu_std",
            "meanDeltaC_mean", "meanDeltaC_std", "chiDeltaC_mean", "chiDeltaC_std",
            "A_mu_mean", "A_mu_std", "A_deltaC_mean", "A_deltaC_std",
            "C_switch_mu_mean", "C_switch_mu_std", "C_switch_deltaC_mean", "C_switch_deltaC_std"
        };
    }

    private static List<String[]> summarizeRows(List<Row> rows) {
        Map<String, List<Row>> groups = new LinkedHashMap<>();
        for (Row row : rows) {
            groups.computeIfAbsent(row.protocol + "|" + row.condition, k -> new ArrayList<>()).add(row);
        }

        List<String[]> out = new ArrayList<>();
        for (List<Row> group : groups.values()) {
            Row first = group.get(0);
            out.add(new String[] {
                first.protocol, first.condition,
                first.dwell < 0 ? "" : Integer.toString(first.dwell),
                finiteOrBlank(first.pLoss), finiteOrBlank(first.pReturn),
                Integer.toString(group.size()),
                mean(group, r -> r.metrics.fractionRF), std(group, r -> r.metrics.fractionRF),
                mean(group, r -> r.metrics.meanDFEpisodeTime), std(group, r -> r.metrics.meanDFEpisodeTime),
                mean(group, r -> r.metrics.transitionRate), std(group, r -> r.metrics.transitionRate),
                mean(group, r -> r.metrics.meanMu), std(group, r -> r.metrics.meanMu),
                mean(group, r -> r.metrics.meanDeltaC), std(group, r -> r.metrics.meanDeltaC),
                mean(group, r -> r.metrics.susceptibility), std(group, r -> r.metrics.susceptibility),
                mean(group, r -> r.metrics.amplitudeMu), std(group, r -> r.metrics.amplitudeMu),
                mean(group, r -> r.metrics.amplitudeDeltaC), std(group, r -> r.metrics.amplitudeDeltaC),
                mean(group, r -> r.penaltyMu), std(group, r -> r.penaltyMu),
                mean(group, r -> r.penaltyDC), std(group, r -> r.penaltyDC)
            });
        }
        return out;
    }

    private interface Value { double get(Row row); }

    private static String mean(List<Row> rows, Value f) {
        double sum = 0.0;
        for (Row row : rows) sum += f.get(row);
        return Double.toString(sum / rows.size());
    }

    private static String std(List<Row> rows, Value f) {
        double sum = 0.0;
        for (Row row : rows) sum += f.get(row);
        double mean = sum / rows.size();
        double ss = 0.0;
        for (Row row : rows) {
            double d = f.get(row) - mean;
            ss += d * d;
        }
        return Double.toString(Math.sqrt(ss / rows.size()));
    }

    private static void writeTimeSeries(Path file, SimulationResult r) throws IOException {
        List<String[]> rows = new ArrayList<>();
        int n = r.time.size();
        for (int i = 0; i < n; i++) {
            rows.add(new String[] {
                Double.toString(r.time.get(i)),
                Integer.toString(r.orderingRF.get(i)),
                Integer.toString(r.orderingSwitchFlag.get(i)),
                Double.toString(r.mu.get(i)),
                Double.toString(r.coherence.get(i)),
                Double.toString(r.entropy.get(i)),
                Double.toString(r.effectiveNoise.get(i))
            });
        }
        Csv.writeRows(file,
                new String[] {"t", "orderingRF", "switchFlag", "mu", "dC", "SvN", "effectiveNoise"},
                rows);
    }

    private static void writeMetricDefinitions(Path file, RunConfig cfg) throws IOException {
        List<String> lines = List.of(
            "RF/DF switching study metric definitions",
            "",
            "Post-burn-in samples omitted from metrics: " + cfg.switchingBurnInSamples,
            "meanMu = temporal mean of regulation gain mu.",
            "meanDeltaC = temporal mean of coherence gap Delta C.",
            "chiDeltaC = Var_t[Delta C(t)] over the retained samples.",
            "A_mu = max_t mu(t) - min_t mu(t) over the retained samples.",
            "A_deltaC = max_t Delta C(t) - min_t Delta C(t) over the retained samples.",
            "fractionRF = fraction of retained samples using REGULATION_FIRST.",
            "meanDFEpisodeTime = mean contiguous DISTURBANCE_FIRST episode length in model time.",
            "expectedMuMixture = fractionRF * meanMu_RF + (1-fractionRF) * meanMu_DF,",
            "  where fixed RF and fixed DF baselines share the same disturbance seed as the switching run.",
            "C_switch_mu = meanMu_switching - expectedMuMixture.",
            "C_switch_mu > 0 means switching adds regulatory burden beyond occupancy-weighted fixed controls.",
            "C_switch_deltaC is defined analogously for the coherence gap.",
            "", 
            "Additional Paper 3 negative-penalty diagnostics:",
            "switching_stationarity_runs.csv divides each retained trajectory into four equal windows.",
            "muLateDrift = muWindow4 - muWindow3; muFinalQuarterSlope is an OLS slope versus model time.",
            "switching_event_aligned.csv measures response after transitions while the new mode remains active.",
            "DF_TO_RF excess is relative to the matched fixed-RF trajectory at the same sample time.",
            "RF_TO_DF excess is relative to the matched fixed-DF trajectory at the same sample time."
        );
        Files.write(file, lines);
    }

    private static void validate(RunConfig cfg) {
        if (cfg.switchingRuns < 1) {
            throw new IllegalArgumentException("switchingRuns must be >= 1");
        }
        if (cfg.switchingBurnInSamples < 0) {
            throw new IllegalArgumentException("switchingBurnInSamples must be >= 0");
        }
    }

    private static int[] parseIntGrid(String text) {
        String[] pieces = text.split(",");
        int[] out = new int[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            out[i] = Integer.parseInt(pieces[i].trim());
            if (out[i] < 1) throw new IllegalArgumentException("Dwell durations must be >= 1");
        }
        return out;
    }

    private static double[][] parsePairGrid(String text) {
        String[] pieces = text.split(",");
        double[][] out = new double[pieces.length][2];
        for (int i = 0; i < pieces.length; i++) {
            String[] pair = pieces[i].trim().split(":");
            if (pair.length != 2) {
                throw new IllegalArgumentException("Markov pair must be pLoss:pReturn: " + pieces[i]);
            }
            out[i][0] = Double.parseDouble(pair[0].trim());
            out[i][1] = Double.parseDouble(pair[1].trim());
            checkProb(out[i][0]);
            checkProb(out[i][1]);
        }
        return out;
    }

    private static void checkProb(double p) {
        if (!Double.isFinite(p) || p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Transition probabilities must be in [0,1]");
        }
    }

    private static long mixedSwitchSeed(long base, int protocol, int condition, int replicate) {
        long x = base;
        x ^= 0x9E3779B97F4A7C15L * (protocol + 1L);
        x ^= 0xBF58476D1CE4E5B9L * (condition + 1L);
        x ^= 0x94D049BB133111EBL * (replicate + 1L);
        x ^= x >>> 30;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 27;
        x *= 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }

    private static String finiteOrBlank(double value) {
        return Double.isFinite(value) ? Double.toString(value) : "";
    }

    private static String formatKey(double value) {
        return String.format(Locale.US, "%.4g", value).replace('.', 'p');
    }
}
