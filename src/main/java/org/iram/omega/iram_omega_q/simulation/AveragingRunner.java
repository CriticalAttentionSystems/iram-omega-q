/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.iram.omega.iram_omega_q.simulation.RegulationSimulation.SimulationResult;

/**
 *
 * @author veronique
 */
public class AveragingRunner {

    public static AveragedResult run(
            SimulationParameters params,
            int runs) {

        List<SimulationResult> all = new ArrayList<>();

        for (int i = 0; i < runs; i++) {
            all.add(RegulationSimulation.run(params));
        }

        return average(all);
    }

    private static AveragedResult average(
            List<SimulationResult> runs) {

        int T = runs.get(0).time.size();
        AveragedResult a = new AveragedResult();

        a.time = runs.get(0).time;

        a.meanEntropy = mean(runs, r -> r.entropy, T);
        a.stdEntropy  = std (runs, r -> r.entropy, T);

        a.meanCoherence = mean(runs, r -> r.coherence, T);
        a.stdCoherence  = std (runs, r -> r.coherence, T);

        a.meanMu = mean(runs, r -> r.mu, T);
        a.stdMu  = std (runs, r -> r.mu, T);

        return a;
    }

    /**
     * 
     * @param runs
     * @param f
     * @param T
     * @return 
     */
    private static List<Double> mean(
            List<SimulationResult> runs,
            Function<SimulationResult, List<Double>> f,
            int T) {

        List<Double> m = new ArrayList<>();
        for (int t = 0; t < T; t++) {
            double s = 0;
            for (SimulationResult r : runs) {
                s += f.apply(r).get(t);
            }
            m.add(s / runs.size());
        }
        return m;
    }

    private static List<Double> std(
            List<SimulationResult> runs,
            Function<SimulationResult, List<Double>> f,
            int T) {

        List<Double> s = new ArrayList<>();
        for (int t = 0; t < T; t++) {
            double m = 0;
            for (SimulationResult r : runs) {
                m += f.apply(r).get(t);
            }
            m /= runs.size();

            double v = 0;
            for (SimulationResult r : runs) {
                double d = f.apply(r).get(t) - m;
                v += d * d;
            }
            s.add(Math.sqrt(v / runs.size()));
        }
        return s;
    }
}
