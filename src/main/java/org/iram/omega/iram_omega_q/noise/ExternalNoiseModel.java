/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.noise;

/**
 *
 * @author veronique
 */

/**
 * ExternalNoiseModel
 *
 * Baseline external disturbance source.
 *
 * This model preserves the original IRAM-Ω-Q behavior: the simulation sets
 * the current external-noise amplitude through QuantumRegulationAgent
 * setEmotionalNoise(...), and the agent passes that value through
 * NoiseContext.baseExternalNoise.
 *
 * This class is stateless.
 */
public final class ExternalNoiseModel implements NoiseModel {

    public ExternalNoiseModel() {
    }

    @Override
    public double noiseAt(NoiseContext ctx) {
        return NoiseModel.requireValidEta(ctx.baseExternalNoise);
    }

    @Override
    public String name() {
        return "EXTERNAL";
    }
}