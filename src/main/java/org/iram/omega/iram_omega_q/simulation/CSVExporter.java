/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import org.iram.omega.iram_omega_q.simulation.RegulationSimulation.SimulationResult;

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

            // --- Header: full reproducibility metadata ---
            out.println("# Quantum Regulation Simulation");
            out.println("# parameters:");
            out.println("# targetEntropy=" + params.targetEntropy);
            out.println("# emotionalNoise=" + params.emotionalNoise);
            out.println("# learningRate beta0=" + params.muTargetGain
                    + " , alpha0=" + params.muDerivativeGain);
            out.println("# ordering=" + params.ordering);

            // Paper 4 noise-source metadata.
            out.println("# noiseModelType=" + params.noiseModelType);
            out.println("# selfNoiseRho=" + params.selfNoiseRho);
            out.println("# selfNoiseSigma=" + params.selfNoiseSigma);
            out.println("# inducedNoiseAlpha=" + params.inducedNoiseAlpha);
            out.println("# noiseSeedOffset=" + params.noiseSeedOffset);

            out.println();

            // --- CSV header ---
            out.println(
                    "time,"
                    + "entropy,"
                    + "coherence,"
                    + "mu,"
                    + "deltaMu,"
                    + "targetEntropy,"
                    + "rawNoise,"
                    + "effectiveNoise,"
                    + "deltaMuPreNoise,"
                    + "deltaMuPostUpdate,"
                    + "interventionFlag,"
                    + "resetFlag,"
                    + "orderingRF,"
                    + "orderingSwitchFlag"
            );

            for (int i = 0; i < r.time.size(); i++) {

                out.printf(
                        java.util.Locale.US,
                        "%.5f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%d,%d,%d,%d%n",
                        r.time.get(i),
                        r.entropy.get(i),
                        r.coherence.get(i),
                        r.mu.get(i),
                        r.deltaMu.get(i),
                        r.targetEntropy.get(i),
                        r.rawNoise.get(i),
                        r.effectiveNoise.get(i),
                        r.deltaMuPreNoise.get(i),
                        r.deltaMuPostUpdate.get(i),
                        r.interventionFlag.get(i),
                        r.resetFlag.get(i),
                        r.orderingRF.get(i),
                        r.orderingSwitchFlag.get(i)
                );
            }
        }
    }
}
