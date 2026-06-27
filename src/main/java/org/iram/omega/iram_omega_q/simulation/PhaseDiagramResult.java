/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;

/**
 *
 * @author veronique
 */
public class PhaseDiagramResult {

    public final double[] noiseValues;
    public final double[] muValues;
    public final double[][] meanCoherence;
    public final double[][] susceptibility;

    public PhaseDiagramResult(
        double[] noiseValues,
        double[] muValues,          
        double[][] meanCoherence,
        double[][] susceptibility
    ) {
        this.noiseValues = noiseValues;
        this.muValues = muValues;
        this.meanCoherence = meanCoherence;
        this.susceptibility = susceptibility;
    }
}