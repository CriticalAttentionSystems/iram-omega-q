/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import org.iram.omega.iram_omega_q.simulation.ConsciousSimulation.SimulationResult;

/**
 *
 * @author veronique
 */
public class CSVExporter {

    public static void export(
            SimulationParameters params,
            SimulationResult r,
            File file) throws IOException {

        try (PrintWriter out = new PrintWriter(file)) {

            // --- Header (full reproducibility) ---
            out.println("# Quantum Consciousness Simulation");
            out.println("# parameters:");
            out.println("# targetEntropy=" + params.targetEntropy);
            out.println("# emotionalNoise=" + params.emotionalNoise);
            out.println("# learningRate alpha0=" + params.muTargetGain+" , beta0 = "+params.muDerivativeGain);
            out.println("# ordering=" + params.ordering);
            out.println();

            out.println("time,entropy,coherence,mu");

            for (int i = 0; i < r.time.size(); i++) {
                out.printf(
                    "%.5f,%.6f,%.6f,%.6f%n",
                    r.time.get(i),
                    r.entropy.get(i),
                    r.coherence.get(i),
                    r.mu.get(i)
                );
            }
        }
    }
}
