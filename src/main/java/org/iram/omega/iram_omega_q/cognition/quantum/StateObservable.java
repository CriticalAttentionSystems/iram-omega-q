/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.cognition.quantum;

/**
 *
 * @author veronique
 */
public class StateObservable {

    private final double[] projector; // GWT-like workspace projection

    public StateObservable(int dim) {
        projector = new double[dim];
        for (int i = 0; i < dim / 3; i++) {
            projector[i] = 1.0; // workspace subspace
        }
    }

    public double measure(CognitiveState state) {
        double sum = 0.0;
        double[][] rho = state.getDensityMatrix();
        for (int i = 0; i < projector.length; i++) {
            sum += rho[i][i] * projector[i];
        }
        return sum;
    }
}

