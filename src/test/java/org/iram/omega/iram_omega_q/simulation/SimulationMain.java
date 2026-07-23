/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;

import java.util.Random;
import org.iram.omega.iram_omega_q.cognition.Hamiltonian;
import org.iram.omega.iram_omega_q.cognition.QuantumCognitiveState;
import org.iram.omega.iram_omega_q.cognition.QuantumRegulationAgent;
import org.iram.omega.iram_omega_q.cognition.QuantumRegulationAgent.ControlOrdering;
import org.iram.omega.iram_omega_q.cognition.quantum.CognitiveState;
import org.iram.omega.iram_omega_q.cognition.quantum.CognitiveStateMetrics;
import org.iram.omega.iram_omega_q.control.MuController;
/**
 *
 * @author veronique
 */


public class SimulationMain {
    public static void main(String[] args) throws Exception {
        Random rng = new Random();
        int steps = 10_000;
        double dt = 0.01;

        // --- Initial quantum cognitive state ---
        int dim = 16;
        double[] salience = new double[dim];

        // focused but not singular
        for (int i = 0; i < dim; i++) {
            salience[i] = Math.exp(-0.5 * Math.pow((i - 6) / 2.0, 2));
        }

        QuantumCognitiveState psi =
            QuantumCognitiveState.salienceCoherent(salience, 0.2, rng);


        // --- Cognitive Hamiltonian ---
        double[] energies = new double[dim];
        for (int i = 0; i < dim; i++) {
            energies[i] = 0.1 * Math.sin(i * 0.7); // mild cognitive landscape
        }

        Hamiltonian H =
            Hamiltonian.attentional(
                energies,
                0.05,   // associative strength
                2.0     // conceptual locality
            );


        MuController muController =
            new MuController(
                0.2,    // muInit
                0.05,   // muMin
                1.0,    // muMax
                0.5,   // alpha0
                0.5,  // beta0
                0.3     // target entropy
            );

        // --- Conscious agent ---
        QuantumRegulationAgent agent =
            new QuantumRegulationAgent(psi, H, muController, rng);

        agent.setEmotionalNoise(0.02);
        agent.setMindfulnessFocus(0);   
        agent.setControlOrdering(ControlOrdering.REGULATION_FIRST);
        
        // --- Simulation loop ---
        for (int t = 0; t < steps; t++) {

            agent.step();   // ← ALL cognition + control happens here

            // ---- Observation only ----
            CognitiveState rho = psi.toCognitiveState();

            double SvN =
                CognitiveStateMetrics.vonNeumannEntropy(psi);

            double coherence =
                CognitiveStateMetrics.coherenceGap(rho);

            double mu =
                agent.mindfulnessLevel();

            if (t % 500 == 0) {
                System.out.printf(
                    "t=%d  SvN=%.4f  C=%.4f  μ=%.3f%n",
                    t, SvN, coherence, mu
                );
            }
        }

        // --- Final measurement (optional) ---
        int outcome = agent.measure();
        System.out.println("Final measurement outcome: " + outcome);
    }
}
