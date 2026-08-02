/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.cognition.quantum;

/**
 *
 * @author veronique
 */
public class CognitiveState {

    private final int dim;
    private double[][] rho;   // real-valued for now 

    public CognitiveState(int dim) {
        this.dim = dim;
        this.rho = new double[dim][dim];
        initializeUniform();
    }

    private void initializeUniform() {
        double p = 1.0 / dim;
        for (int i = 0; i < dim; i++) {
            rho[i][i] = p;
        }
    }

    public double[][] getDensityMatrix() {
        return rho;
    }

    public void normalize() {
        double trace = 0;
        for (int i = 0; i < dim; i++) trace += rho[i][i];
        for (int i = 0; i < dim; i++) rho[i][i] /= trace;
    }
}