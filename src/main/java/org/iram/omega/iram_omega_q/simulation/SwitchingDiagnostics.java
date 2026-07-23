/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.iram.omega.iram_omega_q.simulation.RegulationSimulation.SimulationResult;

/**
 *
 * @author veronique
 */
/**
 * Compact diagnostics for Paper 3 RF/DF switching analyses.
 *
 * <p>This class extracts finite-time stationarity information and transition-
 * aligned responses while a SimulationResult is still available in memory.
 * It intentionally avoids writing every full trajectory for every replicate.</p>
 */
public final class SwitchingDiagnostics {

    private SwitchingDiagnostics() { }

    /** One compact stationarity row for one trajectory. */
    public static final class Stationarity {
        public final double muW1;
        public final double muW2;
        public final double muW3;
        public final double muW4;
        public final double dCW1;
        public final double dCW2;
        public final double dCW3;
        public final double dCW4;
        public final double muLateDrift;
        public final double dCLateDrift;
        public final double muFinalQuarterSlope;
        public final double dCFinalQuarterSlope;
        public final int retainedSamples;

        private Stationarity(double muW1, double muW2, double muW3, double muW4,
                double dCW1, double dCW2, double dCW3, double dCW4,
                double muLateDrift, double dCLateDrift,
                double muFinalQuarterSlope, double dCFinalQuarterSlope,
                int retainedSamples) {
            this.muW1 = muW1;
            this.muW2 = muW2;
            this.muW3 = muW3;
            this.muW4 = muW4;
            this.dCW1 = dCW1;
            this.dCW2 = dCW2;
            this.dCW3 = dCW3;
            this.dCW4 = dCW4;
            this.muLateDrift = muLateDrift;
            this.dCLateDrift = dCLateDrift;
            this.muFinalQuarterSlope = muFinalQuarterSlope;
            this.dCFinalQuarterSlope = dCFinalQuarterSlope;
            this.retainedSamples = retainedSamples;
        }

        public String[] asCsv(String protocol, String condition, int replicate) {
            return new String[] {
                protocol, condition, Integer.toString(replicate),
                Integer.toString(retainedSamples),
                Double.toString(muW1), Double.toString(muW2),
                Double.toString(muW3), Double.toString(muW4),
                Double.toString(muLateDrift), Double.toString(muFinalQuarterSlope),
                Double.toString(dCW1), Double.toString(dCW2),
                Double.toString(dCW3), Double.toString(dCW4),
                Double.toString(dCLateDrift), Double.toString(dCFinalQuarterSlope)
            };
        }
    }

    public static String[] stationarityHeader() {
        return new String[] {
            "protocol", "condition", "replicate", "retainedSamples",
            "muWindow1", "muWindow2", "muWindow3", "muWindow4",
            "muLateDrift", "muFinalQuarterSlope",
            "dCWindow1", "dCWindow2", "dCWindow3", "dCWindow4",
            "dCLateDrift", "dCFinalQuarterSlope"
        };
    }

    /**
     * Divide the retained post-burn-in trajectory into four equal windows.
     * The late-drift diagnostic is window 4 minus window 3; final-quarter
     * slope is an ordinary least-squares slope versus model time.
     */
    public static Stationarity stationarity(SimulationResult run, int burnInSamples) {
        int n = commonLength(run);
        if (burnInSamples < 0 || burnInSamples >= n - 8) {
            throw new IllegalArgumentException("Insufficient retained samples for stationarity diagnostics: burnIn="
                    + burnInSamples + " samples=" + n);
        }
        int start = burnInSamples;
        int retained = n - start;
        int q = retained / 4;
        if (q < 2) {
            throw new IllegalArgumentException("Need at least 8 retained samples for four-window stationarity check.");
        }
        int a = start;
        int b = a + q;
        int c = b + q;
        int d = c + q;
        int e = n; // place any remainder into the final window

        double mu1 = mean(run.mu, a, b);
        double mu2 = mean(run.mu, b, c);
        double mu3 = mean(run.mu, c, d);
        double mu4 = mean(run.mu, d, e);
        double dc1 = mean(run.coherence, a, b);
        double dc2 = mean(run.coherence, b, c);
        double dc3 = mean(run.coherence, c, d);
        double dc4 = mean(run.coherence, d, e);
        return new Stationarity(
                mu1, mu2, mu3, mu4,
                dc1, dc2, dc3, dc4,
                mu4 - mu3, dc4 - dc3,
                slope(run.time, run.mu, d, e),
                slope(run.time, run.coherence, d, e),
                retained);
    }

    public static void writeEventHeader(BufferedWriter writer) throws IOException {
        writer.write("protocol,condition,replicate,transition,lagSteps,tau,availableEvents,excessMuMean,excessDeltaCMean");
        writer.newLine();
    }

    /**
     * Write compact transition-aligned response averages for one switching
     * trajectory. DF_TO_RF is compared to the matched fixed-RF trajectory;
     * RF_TO_DF is compared to matched fixed-DF. A lag is retained only while
     * the post-transition mode remains active, so subsequent switches do not
     * contaminate the response being measured.
     */
    public static void writeEventAligned(
            BufferedWriter writer,
            String protocol,
            String condition,
            int replicate,
            SimulationResult switching,
            SimulationResult fixedRF,
            SimulationResult fixedDF,
            int burnInSamples,
            int maxLagSteps,
            int lagStrideSteps) throws IOException {

        if (maxLagSteps < 0 || lagStrideSteps < 1) {
            throw new IllegalArgumentException("Invalid event-aligned diagnostic lag controls.");
        }
        writeOneDirection(writer, protocol, condition, replicate, "DF_TO_RF", 1,
                switching, fixedRF, burnInSamples, maxLagSteps, lagStrideSteps);
        writeOneDirection(writer, protocol, condition, replicate, "RF_TO_DF", 0,
                switching, fixedDF, burnInSamples, maxLagSteps, lagStrideSteps);
    }

    private static void writeOneDirection(
            BufferedWriter writer,
            String protocol,
            String condition,
            int replicate,
            String transition,
            int afterModeRF,
            SimulationResult switching,
            SimulationResult matchedFixed,
            int burnInSamples,
            int maxLagSteps,
            int lagStrideSteps) throws IOException {

        int n = Math.min(commonLength(switching), commonLength(matchedFixed));
        List<Integer> onsets = new ArrayList<>();
        for (int i = Math.max(1, burnInSamples); i < n; i++) {
            int previous = switching.orderingRF.get(i - 1);
            int current = switching.orderingRF.get(i);
            if (previous != current && current == afterModeRF) {
                onsets.add(i);
            }
        }
        for (int lag = 0; lag <= maxLagSteps; lag += lagStrideSteps) {
            double sumMu = 0.0;
            double sumDC = 0.0;
            int events = 0;
            for (int onset : onsets) {
                int idx = onset + lag;
                if (idx >= n || !modePersists(switching, onset, idx, afterModeRF)) {
                    continue;
                }
                sumMu += switching.mu.get(idx) - matchedFixed.mu.get(idx);
                sumDC += switching.coherence.get(idx) - matchedFixed.coherence.get(idx);
                events++;
            }
            if (events == 0) {
                continue;
            }
            double tau = switching.time.get(Math.min(n - 1, onsets.get(0) + lag))
                    - switching.time.get(onsets.get(0));
            writer.write(csv(protocol)); writer.write(',');
            writer.write(csv(condition)); writer.write(',');
            writer.write(Integer.toString(replicate)); writer.write(',');
            writer.write(transition); writer.write(',');
            writer.write(Integer.toString(lag)); writer.write(',');
            writer.write(Double.toString(tau)); writer.write(',');
            writer.write(Integer.toString(events)); writer.write(',');
            writer.write(Double.toString(sumMu / events)); writer.write(',');
            writer.write(Double.toString(sumDC / events));
            writer.newLine();
        }
    }

    private static boolean modePersists(SimulationResult run, int start, int end, int requiredRF) {
        for (int i = start; i <= end; i++) {
            if (run.orderingRF.get(i) != requiredRF) {
                return false;
            }
        }
        return true;
    }

    private static int commonLength(SimulationResult run) {
        return Math.min(run.time.size(), Math.min(run.mu.size(),
                Math.min(run.coherence.size(), run.orderingRF.size())));
    }

    private static double mean(List<Double> values, int start, int end) {
        double sum = 0.0;
        for (int i = start; i < end; i++) {
            sum += values.get(i);
        }
        return sum / (end - start);
    }

    private static double slope(List<Double> x, List<Double> y, int start, int end) {
        double mx = mean(x, start, end);
        double my = mean(y, start, end);
        double numerator = 0.0;
        double denominator = 0.0;
        for (int i = start; i < end; i++) {
            double dx = x.get(i) - mx;
            numerator += dx * (y.get(i) - my);
            denominator += dx * dx;
        }
        return denominator == 0.0 ? Double.NaN : numerator / denominator;
    }

    private static String csv(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}

