/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.noise;

/**
 *
 * @author veronique
 */
public final class NoiseSample {
    public final double etaTotal;
    public final double etaExternal;
    public final double etaSelf;
    public final double etaInduced;
    public final double zeta;

    public NoiseSample(
            double etaTotal,
            double etaExternal,
            double etaSelf,
            double etaInduced,
            double zeta
    ) {
        this.etaTotal = etaTotal;
        this.etaExternal = etaExternal;
        this.etaSelf = etaSelf;
        this.etaInduced = etaInduced;
        this.zeta = zeta;
    }
}