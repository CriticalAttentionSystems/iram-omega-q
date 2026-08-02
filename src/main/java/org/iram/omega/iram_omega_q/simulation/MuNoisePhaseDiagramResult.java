/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;

/**
 *
 * @author veronique
 */
public class MuNoisePhaseDiagramResult {

    public final double[] muValues;
    public final double[] noiseValues;

    public final double[][] meanCoherence;
    public final double[][] susceptibility;

    public MuNoisePhaseDiagramResult(
        double[] muValues,
        double[] noiseValues,
        double[][] meanCoherence,
        double[][] susceptibility
    ) {
        this.muValues = muValues;
        this.noiseValues = noiseValues;
        this.meanCoherence = meanCoherence;
        this.susceptibility = susceptibility;
    }
}
