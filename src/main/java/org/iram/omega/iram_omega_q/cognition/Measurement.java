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
public class Measurement {

    /** Sample an index i from |psi_i|^2 using the provided RNG (seeded upstream). */
    public static int sample(QuantumCognitiveState psi, Random rng) {
        final double r = rng.nextDouble();
        double cum = 0.0;

        // Assumes psi is normalized; still robust to small drift.
        for (int i = 0; i < psi.dim(); i++) {
            final double re = psi.real()[i];
            final double im = psi.imag()[i];
            final double p = re * re + im * im;

            cum += p;
            if (r <= cum) return i;
        }

        // If cum < 1 due to numeric drift, fall back to last index.
        return psi.dim() - 1;
    }

}
