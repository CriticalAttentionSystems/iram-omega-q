/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package org.iram.omega.iram_omega_q.experiment;
import java.util.Random;
import org.iram.omega.iram_omega_q.cognition.AttentionHamiltonian;
import org.iram.omega.iram_omega_q.cognition.Hamiltonian;
import org.iram.omega.iram_omega_q.cognition.QuantumCognitiveState;
import org.iram.omega.iram_omega_q.cognition.QuantumConsciousAgent;
import org.iram.omega.iram_omega_q.cognition.quantum.CognitiveState;
import org.iram.omega.iram_omega_q.cognition.quantum.CognitiveStateMetrics;
import org.iram.omega.iram_omega_q.control.MuController;
/**
 *
 * @author veronique
 */


public class ConsciousnessExperiment {

    private final int dim;
    private final int steps;
    private final double dt;
    private final Random rng;
    public ConsciousnessExperiment(int dim, int steps, double dt) {
        this.dim = dim;
        this.steps = steps;
        this.dt = dt;
        rng = new Random();
    }

    public ExperimentResult run() {

        // --- initial state ---
        QuantumCognitiveState psi = new QuantumCognitiveState(dim);
        Hamiltonian H = new AttentionHamiltonian(0.05);

        
        
        MuController muController =
            new MuController(
                0.2,    // muInit
                0.05,   // muMin
                1.0,    // muMax
                0.01,   // alpha0
                0.005,  // beta0
                0.5     // target entropy
            );

        QuantumConsciousAgent agent =
            new QuantumConsciousAgent(
                psi,
                H,
                muController,
                rng    
            );


        agent.setEmotionalNoise(0.02);

        ExperimentResult result = new ExperimentResult();

        // --- time loop ---
        for (int t = 0; t < steps; t++) {

            // Step quantum dynamics
            agent.step();

            // Project ψ → ρ
            CognitiveState rho = psi.toCognitiveState();

            // Metrics
            double SvN =
                CognitiveStateMetrics.vonNeumannEntropy(psi);
            double Sdiag =
                CognitiveStateMetrics.cognitiveEntropy(rho);
            double coherence =
                CognitiveStateMetrics.coherenceGap(rho);

            // Adaptive regulation
            double mu = muController.update(SvN);

            // Log
            result.record(t, SvN, Sdiag, coherence, mu);
        }

        return result;
    }
}
