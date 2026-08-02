/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author veronique
 */
public class PhaseSweep {

    public static Map<Double, Double> sweepTargetEntropy(
            SimulationParameters base,
            double[] targets,
            int runs,
            int tailWindow) {

        Map<Double, Double> phase = new LinkedHashMap<>();

        for (double target : targets) {
            SimulationParameters p = base.copy();
            p.targetEntropy = target;

            AveragedResult r =
                AveragingRunner.run(p, runs);

            double steadyMu =
                meanTail(r.meanMu, tailWindow);

            phase.put(target, steadyMu);
        }
        return phase;
    }

    private static double meanTail(
            List<Double> x,
            int n) {

        int start = Math.max(0, x.size() - n);
        double s = 0;
        for (int i = start; i < x.size(); i++) {
            s += x.get(i);
        }
        return s / (x.size() - start);
    }
}
