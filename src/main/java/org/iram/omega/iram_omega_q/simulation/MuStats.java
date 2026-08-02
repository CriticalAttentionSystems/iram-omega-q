/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;

/**
 *
 * @author veronique
 */
public final class MuStats {
    public final double mean;
    public final double std;
    public final int nUsed;
    public final int nTotal;

    private MuStats(double mean, double std, int nUsed, int nTotal) {
        this.mean = mean;
        this.std = std;
        this.nUsed = nUsed;
        this.nTotal = nTotal;
    }

    /** Compute mean/std of μc(η) ignoring NaNs and excluding boundary hits at muMin/muMax. */
    public static MuStats from(double[] muCritical, double muMin, double muMax) {
        if (muCritical == null || muCritical.length == 0) {
            return new MuStats(Double.NaN, Double.NaN, 0, 0);
        }

        double sum = 0.0;
        int n = 0;

        // 1) mean over interior finite points
        for (double v : muCritical) {
            if (!Double.isFinite(v)) continue;
            // Exclude boundary hits (within tiny tolerance)
            if (approxEq(v, muMin) || approxEq(v, muMax)) continue;
            sum += v;
            n++;
        }

        if (n == 0) return new MuStats(Double.NaN, Double.NaN, 0, muCritical.length);

        double mean = sum / n;

        // 2) unbiased sample std
        double ss = 0.0;
        for (double v : muCritical) {
            if (!Double.isFinite(v)) continue;
            if (approxEq(v, muMin) || approxEq(v, muMax)) continue;
            double d = v - mean;
            ss += d * d;
        }

        double std = (n >= 2) ? Math.sqrt(ss / (n - 1)) : 0.0;

        return new MuStats(mean, std, n, muCritical.length);
    }

    private static boolean approxEq(double a, double b) {
        return Math.abs(a - b) <= 1e-12;
    }
}