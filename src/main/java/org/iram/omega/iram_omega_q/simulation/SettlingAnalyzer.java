/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;

/**
 *
 * @author veronique
 */
public final class SettlingAnalyzer {

    private SettlingAnalyzer() {}

    /**
     * Returns the first recorded sample index i such that
     * |deltaMu| < eps for window consecutive recorded samples.
     * Returns -1 if no settling is detected.
     */
    public static int detectSettlingIndex(
            ConsciousSimulation.SimulationResult r,
            double eps,
            int window
    ) {
        if (r == null || r.deltaMu == null || r.deltaMu.isEmpty()) {
            return -1;
        }
        if (window <= 0) {
            throw new IllegalArgumentException("window must be > 0");
        }

        int streak = 0;
        for (int i = 0; i < r.deltaMu.size(); i++) {
            double dmu = Math.abs(r.deltaMu.get(i));

            if (dmu < eps) {
                streak++;
                if (streak >= window) {
                    return i - window + 1;
                }
            } else {
                streak = 0;
            }
        }

        return -1;
    }

    /**
     * Returns the settling time using r.time if available, else NaN.
     */
    public static double detectSettlingTime(
            ConsciousSimulation.SimulationResult r,
            double eps,
            int window
    ) {
        int idx = detectSettlingIndex(r, eps, window);
        if (idx < 0 || r.time == null || idx >= r.time.size()) {
            return Double.NaN;
        }
        return r.time.get(idx);
    }

    /**
     * Fraction of recorded samples satisfying |deltaMu| < eps.
     * Useful as a softer companion metric.
     */
    public static double settledFraction(
            ConsciousSimulation.SimulationResult r,
            double eps
    ) {
        if (r == null || r.deltaMu == null || r.deltaMu.isEmpty()) {
            return Double.NaN;
        }

        int count = 0;
        for (double dmu : r.deltaMu) {
            if (Math.abs(dmu) < eps) {
                count++;
            }
        }

        return (double) count / r.deltaMu.size();
    }
}