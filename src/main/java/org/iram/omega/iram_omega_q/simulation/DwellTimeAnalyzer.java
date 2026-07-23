/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author veronique
 */
/**
 * Analyzes residence times and transitions between coherence-gap regimes.
 *
 * A regime is a labeled range of the coherence-gap diagnostic ΔC:
 *
 *     LOW   if ΔC < lowThreshold
 *     MID   if lowThreshold <= ΔC <= highThreshold
 *     HIGH  if ΔC > highThreshold
 *
 * A dwell is one maximal contiguous period during which the simulated
 * trajectory remains in the same regime. For example, if the recorded
 * labels are:
 *
 *     LOW, LOW, LOW, MID, MID, HIGH
 *
 * then the trajectory contains:
 *
 *     one LOW dwell,
 *     one MID dwell,
 *     one HIGH dwell.
 *
 * A completed dwell is a dwell for which a transition into a different
 * regime is observed. Its duration is measured from the first observation
 * in the regime to the first subsequent observation outside that regime.
 *
 * A final dwell that is still active when the recorded trajectory ends is
 * right-censored: the simulation shows how long the system remained in the
 * regime up to the end of observation, but not when it would eventually
 * leave that regime.
 */
public final class DwellTimeAnalyzer {

    public static final int LOW = 0;
    public static final int MID = 1;
    public static final int HIGH = 2;

    private DwellTimeAnalyzer() {}

    /**
    * Assign each recorded sample to a coherence-gap regime.
    *
    * The regime label identifies the dynamical condition occupied by the
    * trajectory at that observation time:
    *
    *     LOW   if ΔC < lowThreshold
    *     MID   if lowThreshold <= ΔC <= highThreshold
    *     HIGH  if ΔC > highThreshold
    *
    * Consecutive samples with the same label belong to the same dwell.
    *
    * @param r simulation trajectory containing the recorded coherence gap ΔC
    * @param lowThreshold upper boundary of the LOW regime
    * @param highThreshold lower boundary of the HIGH regime
    * @return one regime label for each recorded coherence-gap sample
    */
    public static List<Integer> labelRegimes(
            RegulationSimulation.SimulationResult r,
            double lowThreshold,
            double highThreshold
    ) {
        if (lowThreshold > highThreshold) {
            throw new IllegalArgumentException("lowThreshold must be <= highThreshold");
        }

        List<Integer> labels = new ArrayList<>();
        if (r == null || r.coherence == null) {
            return labels;
        }

        for (double c : r.coherence) {
            if (c < lowThreshold) {
                labels.add(LOW);
            } else if (c > highThreshold) {
                labels.add(HIGH);
            } else {
                labels.add(MID);
            }
        }

        return labels;
    }

    /**
     * Returns dwell lengths in number of recorded samples
     * for contiguous runs of targetLabel.
     */
    public static List<Integer> computeDwellLengths(
            List<Integer> labels,
            int targetLabel
    ) {
        List<Integer> lengths = new ArrayList<>();
        if (labels == null || labels.isEmpty()) {
            return lengths;
        }

        int streak = 0;
        for (int x : labels) {
            if (x == targetLabel) {
                streak++;
            } else if (streak > 0) {
                lengths.add(streak);
                streak = 0;
            }
        }
        if (streak > 0) {
            lengths.add(streak);
        }

        return lengths;
    }

    /**
    * Compute dwell lengths in units of recorded samples.
    *
    * A dwell is a maximal contiguous sequence of samples having the requested
    * regime label. For example:
    *
    *     LOW, LOW, MID, HIGH, HIGH, HIGH
    *
    * contains a LOW dwell of length 2 and a HIGH dwell of length 3.
    *
    * This method counts samples only; it does not convert dwell lengths into
    * physical simulation time.
    *
    * @param labels regime label assigned to each recorded sample
    * @param targetLabel regime whose dwell lengths are requested
    * @return sample counts for all observed dwells in the target regime
    */
    public static List<Double> computeDwellTimes(
        RegulationSimulation.SimulationResult r,
        List<Integer> labels,
        int targetLabel
    ) {
        List<Double> out = new ArrayList<>();

        if (r == null || r.time == null || labels == null || labels.isEmpty()) {
            return out;
        }

        if (r.time.size() != labels.size()) {
            throw new IllegalArgumentException("time and labels must have same length");
        }

        int start = -1;

        for (int i = 0; i < labels.size(); i++) {

            if (labels.get(i) == targetLabel) {
                if (start < 0) {
                    start = i;
                }
            } else if (start >= 0) {
                /*
                 * A transition out of the target regime is observed at sample i.
                 * The completed dwell therefore extends from its entry time to
                 * the first recorded time outside the regime.
                 */
                out.add(r.time.get(i) - r.time.get(start));
                start = -1;
            }
        }

        if (start >= 0) {
            /*
             * The trajectory ends while still in the target regime.
             * This final dwell is right-censored: record only the residence time
             * directly established by the observed trajectory.
             */
            out.add(r.time.get(r.time.size() - 1) - r.time.get(start));
        }

        return out;
    }
    
    /**
    * Compute the rate of observed transitions between regimes.
    *
    * A transition occurs whenever two consecutive recorded samples have
    * different regime labels. This diagnostic measures how often the
    * trajectory changes coherence-gap regime per unit of observed simulation
    * time; it does not estimate the unobserved continuation of a final
    * right-censored dwell.
    *
    * @param r simulation trajectory containing the recorded time axis
    * @param labels regime label assigned to each recorded sample
    * @return number of observed regime changes per unit simulation time
    */
    public static double transitionRate(
            RegulationSimulation.SimulationResult r,
            List<Integer> labels
    ) {
        if (r == null || r.time == null || labels == null || labels.size() < 2) {
            return Double.NaN;
        }
        if (r.time.size() != labels.size()) {
            throw new IllegalArgumentException("time and labels must have same length");
        }

        int transitions = 0;
        for (int i = 1; i < labels.size(); i++) {
            if (!labels.get(i).equals(labels.get(i - 1))) {
                transitions++;
            }
        }

        double duration = r.time.get(r.time.size() - 1) - r.time.get(0);
        if (duration <= 0.0) {
            return Double.NaN;
        }

        return transitions / duration;
    }

    public static double mean(List<Double> xs) {
        if (xs == null || xs.isEmpty()) {
            return Double.NaN;
        }
        double s = 0.0;
        for (double x : xs) {
            s += x;
        }
        return s / xs.size();
    }

}
