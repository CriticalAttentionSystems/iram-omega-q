/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;

import java.io.*;
import java.nio.file.*;
import java.util.Objects;
import java.util.Properties;
import org.iram.omega.iram_omega_q.cognition.QuantumConsciousAgent;

/**
 *
 * @author veronique
 */
public class RunConfig {

    public enum Mode {
        SINGLE,
        AVG,
        SWEEP_EXPLORATORY,
        SWEEP_PUBLICATION,
        HYSTERESIS,
        TARGET_ENTROPY_SWEEP,
        DWELL_TIME,
        AMNESIA,
        PERTURBATION
    }

    // ===== core sim knobs =====
    public int dim = 64;

    public double mu = 0.6;
    public double noise = 0.05;
    public double targetEntropy = 0.5;

    public int steps = 50_000;
    public long seed = 12345L;
    public long baseSeed = 123456789L;

    // Must match QuantumConsciousAgent.ControlOrdering enum constant name
    public String ordering = "REGULATION_FIRST";

    // ===== optional low-level sim knobs =====
    public double dt = 0.01;

    public double salienceCenter = 6.0;
    public double salienceWidth = 2.0;
    public double phaseNoise = 0.2;
    public int focusIndex = 6;

    public double energyScale = 0.15;
    public double coupling = 0.08;
    public double locality = 2.0;

    /*
     * Keep command-line/config runs aligned with SimulationParameters and the
     * GUI defaults: alpha0 is the entropy-derivative gain, beta0 is the
     * target-error gain.
     */
    public double muDerivativeGain = 5e-4;  // alpha0
    public double muTargetGain = 2e-4;      // beta0
    public double muMin = 1e-3;
    public double muMax = 1.0;

    // ===== averaged mode controls =====
    public int avgRuns = 30;
    public int avgBurnInSamples = 2000;

    // ===== sweep controls =====
    public Mode mode = Mode.SINGLE;
    public boolean fast = false;

    public String muGrid = "0.05:1.0:60";
    public String noiseGrid = "1e-4:0.30:40";

    public int burnIn = 5_000;
    public int runsPerPoint = 20;

    // ===== experiment controls =====

    // settling detector
    public double settleEps = 1e-4;
    public int settleWindow = 200;

    // intervention window
    public int interventionStart = -1;
    public int interventionEnd = -1;
    public double muLearningScaleDuringIntervention = 1.0;
    public double noiseMultiplierDuringIntervention = 1.0;
    public Double targetEntropyDuringIntervention = null;

    // reset / amnesia
    public int resetStateAtStep = -1;
    public int resetMuAtStep = -1;
    public boolean keepControllerHistoryOnReset = false;
    public boolean resetToInitialState = true;

    // slow schedules
    public double targetEntropyStart = Double.NaN;
    public double targetEntropyEnd = Double.NaN;
    public SimulationParameters.ScheduleType targetEntropySchedule =
            SimulationParameters.ScheduleType.NONE;

    // dwell-time / regime labeling
    public double focusThresholdLow = 0.05;
    public double focusThresholdHigh = 0.15;

    // hysteresis
    // A true hysteresis loop requires both branches in one continuous run.
    public boolean sweepUp = true;
    public boolean sweepDown = true;

    // metadata
    public String experimentTag = "baseline";

    // ===== output =====
    public String outDir = "sim/results";
    public String runName = "run";

    // ===== threads =====
    public int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    
    public static RunConfig load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            p.load(in);
        }

        RunConfig c = new RunConfig();

        c.dim = getInt(p, "dim", c.dim);

        c.mu = getDouble(p, "mu", c.mu);
        c.noise = getDouble(p, "noise", c.noise);
        c.targetEntropy = getDouble(p, "targetEntropy", c.targetEntropy);

        c.steps = getInt(p, "steps", c.steps);
        c.seed = getLong(p, "seed", c.seed);
        c.baseSeed = getLong(p, "baseSeed", c.baseSeed);

        c.ordering = p.getProperty("ordering", c.ordering).trim();

        c.dt = getDouble(p, "dt", c.dt);

        c.salienceCenter = getDouble(p, "salienceCenter", c.salienceCenter);
        c.salienceWidth = getDouble(p, "salienceWidth", c.salienceWidth);
        c.phaseNoise = getDouble(p, "phaseNoise", c.phaseNoise);
        c.focusIndex = getInt(p, "focusIndex", c.focusIndex);

        c.energyScale = getDouble(p, "energyScale", c.energyScale);
        c.coupling = getDouble(p, "coupling", c.coupling);
        c.locality = getDouble(p, "locality", c.locality);

        c.muTargetGain = getDouble(p, "muTargetGain", c.muTargetGain);
        c.muDerivativeGain = getDouble(p, "muDerivativeGain", c.muDerivativeGain);
        c.muMin = getDouble(p, "muMin", c.muMin);
        c.muMax = getDouble(p, "muMax", c.muMax);

        c.mode = getEnum(p, "mode", c.mode, Mode.class);
        c.fast = getBoolean(p, "fast", c.fast);

        c.threads = getInt(p, "threads", c.threads);
        
        c.muGrid = p.getProperty("muGrid", c.muGrid).trim();
        c.noiseGrid = p.getProperty("noiseGrid", c.noiseGrid).trim();

        c.burnIn = getInt(p, "burnIn", c.burnIn);
        c.runsPerPoint = getInt(p, "runsPerPoint", c.runsPerPoint);

        c.avgRuns = getInt(p, "avgRuns", c.avgRuns);

        Integer legacy = getIntNullable(p, "avgMaxSteps");
        Integer preferred = getIntNullable(p, "avgBurnInSamples");
        if (preferred != null) {
            c.avgBurnInSamples = preferred;
        } else if (legacy != null) {
            c.avgBurnInSamples = legacy;
        }

        c.settleEps = getDouble(p, "settleEps", c.settleEps);
        c.settleWindow = getInt(p, "settleWindow", c.settleWindow);

        c.interventionStart = getInt(p, "interventionStart", c.interventionStart);
        c.interventionEnd = getInt(p, "interventionEnd", c.interventionEnd);
        c.muLearningScaleDuringIntervention =
                getDouble(p, "muLearningScaleDuringIntervention", c.muLearningScaleDuringIntervention);
        c.noiseMultiplierDuringIntervention =
                getDouble(p, "noiseMultiplierDuringIntervention", c.noiseMultiplierDuringIntervention);
        c.targetEntropyDuringIntervention =
                getDoubleNullable(p, "targetEntropyDuringIntervention", c.targetEntropyDuringIntervention);

        c.resetStateAtStep = getInt(p, "resetStateAtStep", c.resetStateAtStep);
        c.resetMuAtStep = getInt(p, "resetMuAtStep", c.resetMuAtStep);
        c.keepControllerHistoryOnReset =
                getBoolean(p, "keepControllerHistoryOnReset", c.keepControllerHistoryOnReset);
        c.resetToInitialState =
                getBoolean(p, "resetToInitialState", c.resetToInitialState);

        c.targetEntropyStart = getDouble(p, "targetEntropyStart", c.targetEntropyStart);
        c.targetEntropyEnd = getDouble(p, "targetEntropyEnd", c.targetEntropyEnd);
        c.targetEntropySchedule =
                getEnum(p, "targetEntropySchedule", c.targetEntropySchedule, SimulationParameters.ScheduleType.class);

        c.focusThresholdLow = getDouble(p, "focusThresholdLow", c.focusThresholdLow);
        c.focusThresholdHigh = getDouble(p, "focusThresholdHigh", c.focusThresholdHigh);

        c.sweepUp = getBoolean(p, "sweepUp", c.sweepUp);
        c.sweepDown = getBoolean(p, "sweepDown", c.sweepDown);

        c.experimentTag = p.getProperty("experimentTag", c.experimentTag).trim();

        c.outDir = p.getProperty("outDir", c.outDir).trim();
        c.runName = p.getProperty("runName", c.runName).trim();

        return c;
    }

    public void save(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);

        Properties p = new Properties();

        p.setProperty("dim", Integer.toString(dim));

        p.setProperty("mu", Double.toString(mu));
        p.setProperty("noise", Double.toString(noise));
        p.setProperty("targetEntropy", Double.toString(targetEntropy));

        p.setProperty("steps", Integer.toString(steps));
        p.setProperty("seed", Long.toString(seed));
        p.setProperty("baseSeed", Long.toString(baseSeed));

        p.setProperty("ordering", ordering);

        p.setProperty("dt", Double.toString(dt));

        p.setProperty("salienceCenter", Double.toString(salienceCenter));
        p.setProperty("salienceWidth", Double.toString(salienceWidth));
        p.setProperty("phaseNoise", Double.toString(phaseNoise));
        p.setProperty("focusIndex", Integer.toString(focusIndex));

        p.setProperty("energyScale", Double.toString(energyScale));
        p.setProperty("coupling", Double.toString(coupling));
        p.setProperty("locality", Double.toString(locality));

        p.setProperty("muDerivativeGain", Double.toString(muDerivativeGain));
        p.setProperty("muTargetGain", Double.toString(muTargetGain));
        p.setProperty("muMin", Double.toString(muMin));
        p.setProperty("muMax", Double.toString(muMax));

        p.setProperty("mode", mode.name());
        p.setProperty("fast", Boolean.toString(fast));

        p.setProperty("muGrid", muGrid);
        p.setProperty("noiseGrid", noiseGrid);

        p.setProperty("burnIn", Integer.toString(burnIn));
        p.setProperty("runsPerPoint", Integer.toString(runsPerPoint));

        p.setProperty("avgRuns", Integer.toString(avgRuns));
        p.setProperty("avgBurnInSamples", Integer.toString(avgBurnInSamples));

        p.setProperty("settleEps", Double.toString(settleEps));
        p.setProperty("settleWindow", Integer.toString(settleWindow));

        p.setProperty("interventionStart", Integer.toString(interventionStart));
        p.setProperty("interventionEnd", Integer.toString(interventionEnd));
        p.setProperty("muLearningScaleDuringIntervention",
                Double.toString(muLearningScaleDuringIntervention));
        p.setProperty("noiseMultiplierDuringIntervention",
                Double.toString(noiseMultiplierDuringIntervention));
        if (targetEntropyDuringIntervention != null) {
            p.setProperty("targetEntropyDuringIntervention",
                    Double.toString(targetEntropyDuringIntervention));
        }

        p.setProperty("resetStateAtStep", Integer.toString(resetStateAtStep));
        p.setProperty("resetMuAtStep", Integer.toString(resetMuAtStep));
        p.setProperty("keepControllerHistoryOnReset",
                Boolean.toString(keepControllerHistoryOnReset));
        p.setProperty("resetToInitialState", Boolean.toString(resetToInitialState));

        p.setProperty("targetEntropyStart", Double.toString(targetEntropyStart));
        p.setProperty("targetEntropyEnd", Double.toString(targetEntropyEnd));
        p.setProperty("targetEntropySchedule", targetEntropySchedule.name());

        p.setProperty("focusThresholdLow", Double.toString(focusThresholdLow));
        p.setProperty("focusThresholdHigh", Double.toString(focusThresholdHigh));

        p.setProperty("sweepUp", Boolean.toString(sweepUp));
        p.setProperty("sweepDown", Boolean.toString(sweepDown));

        p.setProperty("experimentTag", experimentTag);

        p.setProperty("outDir", outDir);
        p.setProperty("runName", runName);

        try (OutputStream out = Files.newOutputStream(path)) {
            p.store(out, "IRAM-Ω Simulation RunConfig");
        }
    }

    public SimulationParameters toSimulationParameters() {
        SimulationParameters sp = new SimulationParameters();

        sp.baseSeed = this.baseSeed;
        sp.seed = this.seed;

        sp.steps = this.steps;
        sp.dt = this.dt;

        sp.dim = this.dim;
        sp.salienceCenter = this.salienceCenter;
        sp.salienceWidth = this.salienceWidth;
        sp.phaseNoise = this.phaseNoise;
        sp.focusIndex = this.focusIndex;

        sp.energyScale = this.energyScale;
        sp.coupling = this.coupling;
        sp.locality = this.locality;

        sp.emotionalNoise = this.noise;
        sp.muInit = this.mu;
        sp.muTargetGain = this.muTargetGain;
        sp.muDerivativeGain = this.muDerivativeGain;
        sp.targetEntropy = this.targetEntropy;

        sp.ordering = QuantumConsciousAgent.ControlOrdering.valueOf(this.ordering.trim().toUpperCase());
        sp.muMin = this.muMin;
        sp.muMax = this.muMax;

        sp.settleEps = this.settleEps;
        sp.settleWindow = this.settleWindow;

        sp.interventionStart = this.interventionStart;
        sp.interventionEnd = this.interventionEnd;
        sp.muLearningScaleDuringIntervention = this.muLearningScaleDuringIntervention;
        sp.noiseMultiplierDuringIntervention = this.noiseMultiplierDuringIntervention;
        sp.targetEntropyDuringIntervention = this.targetEntropyDuringIntervention;

        sp.resetStateAtStep = this.resetStateAtStep;
        sp.resetMuAtStep = this.resetMuAtStep;
        sp.keepControllerHistoryOnReset = this.keepControllerHistoryOnReset;
        sp.resetToInitialState = this.resetToInitialState;

        sp.targetEntropyStart = this.targetEntropyStart;
        sp.targetEntropyEnd = this.targetEntropyEnd;
        sp.targetEntropySchedule = this.targetEntropySchedule;

        sp.focusThresholdLow = this.focusThresholdLow;
        sp.focusThresholdHigh = this.focusThresholdHigh;

        sp.sweepUp = this.sweepUp;
        sp.sweepDown = this.sweepDown;

        sp.experimentTag = this.experimentTag;

        return sp;
    }

    private static Integer getIntNullable(Properties p, String k) {
        String v = p.getProperty(k);
        if (v == null || v.trim().isEmpty()) {
            return null;
        }
        return Integer.valueOf(v.trim());
    }

    private static Double getDoubleNullable(Properties p, String k, Double d) {
        String v = p.getProperty(k);
        if (v == null || v.trim().isEmpty()) {
            return d;
        }
        return Double.valueOf(v.trim());
    }

    private static int getInt(Properties p, String k, int d) {
        String v = p.getProperty(k);
        if (v == null || v.trim().isEmpty()) {
            return d;
        }
        return Integer.parseInt(v.trim());
    }

    private static long getLong(Properties p, String k, long d) {
        String v = p.getProperty(k);
        if (v == null || v.trim().isEmpty()) {
            return d;
        }
        return Long.parseLong(v.trim());
    }

    private static double getDouble(Properties p, String k, double d) {
        String v = p.getProperty(k);
        if (v == null || v.trim().isEmpty()) {
            return d;
        }
        return Double.parseDouble(v.trim());
    }

    private static boolean getBoolean(Properties p, String k, boolean d) {
        String v = p.getProperty(k);
        if (v == null || v.trim().isEmpty()) {
            return d;
        }
        return Boolean.parseBoolean(v.trim());
    }

    private static <E extends Enum<E>> E getEnum(Properties p, String k, E d, Class<E> type) {
        String v = p.getProperty(k);
        if (v == null || v.trim().isEmpty()) {
            return d;
        }
        return Enum.valueOf(type, v.trim().toUpperCase());
    }
    
}