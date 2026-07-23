/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;

import java.util.ArrayList;
import java.util.List;
import org.iram.omega.iram_omega_q.simulation.RegulationSimulation.SimulationResult;

/**
 *
 * @author veronique
 */
public class BatchRunner {

    public static List<SimulationResult> sweepTargetEntropy(
            SimulationParameters base,
            double[] targets) {

        List<SimulationResult> results = new ArrayList<>();

        for (double target : targets) {
            SimulationParameters p = copy(base);
            p.targetEntropy = target;

            SimulationResult r =
                RegulationSimulation.run(p);
            results.add(r);
        }
        return results;
    }

    private static SimulationParameters copy(
            SimulationParameters p) {

        SimulationParameters c = new SimulationParameters();
        c.steps = p.steps;
        c.dt = p.dt;
        c.dim = p.dim;
        c.emotionalNoise = p.emotionalNoise;
        c.muInit = p.muInit;
        c.muDerivativeGain = p.muDerivativeGain; // alpha0
        c.muTargetGain = p.muTargetGain; // beta0
        c.targetEntropy = p.targetEntropy;
        c.ordering = p.ordering;
        c.coupling = p.coupling;
        c.locality = p.locality;
        return c;
    }
}
