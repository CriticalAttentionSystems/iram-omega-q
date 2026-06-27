/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.cognition;

/**
 *
 * @author veronique
 */
public class AttentionHamiltonian implements Hamiltonian {

    private final double coupling;

    public AttentionHamiltonian(double coupling) {
        this.coupling = coupling;
    }

    @Override
    public double get(int i, int j) {
        return i == j ? 0.0 : coupling;
    }
}

