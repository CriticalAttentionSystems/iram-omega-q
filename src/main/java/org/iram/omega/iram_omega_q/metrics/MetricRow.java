/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.metrics;

/**
 *
 * @author veronique
 */


/**
 * Immutable record of per-agent or per-system metrics
 * used for phase-transition and stability analysis.
 */
public record MetricRow(
        int timestep,
        String agentId,
        int agentCount,
        double integrity,
        double awareness,
        double emotionalActivity,
        double reputation,
        String regime,
        boolean collapsed
    ) {

    /** Convenience: final-timestep filter */
    public boolean isFinalTimestep(int maxTimestep) {
        return timestep == maxTimestep;
    }

    public int timestep() { return timestep; }
}
