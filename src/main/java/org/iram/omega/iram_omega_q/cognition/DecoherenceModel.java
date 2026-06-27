/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.cognition;
import java.util.Random;
/**
 *
 * @author veronique
 */
public class DecoherenceModel {

    /** Apply decoherence noise using the provided RNG (seeded upstream). */
    public static void apply(QuantumCognitiveState psi, double noise, Random rng) {
        for (int i = 0; i < psi.dim(); i++) {
            psi.imag()[i] += noise * (rng.nextDouble() - 0.5);
        }
        psi.normalize();
    }
}
