/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;

/**
 *
 * @author veronique
 */
public class SweepResult {
    public final double[] muGrid;
    public final double[] noiseGrid;
    public final double[][] meanEntropy;

    public SweepResult(double[] muGrid, double[] noiseGrid, double[][] meanEntropy) {
        this.muGrid = muGrid;
        this.noiseGrid = noiseGrid;
        this.meanEntropy = meanEntropy;
    }
}

