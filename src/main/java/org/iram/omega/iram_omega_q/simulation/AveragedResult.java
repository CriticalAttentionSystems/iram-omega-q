/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;

import java.util.List;

/**
 *
 * @author veronique
 */
public class AveragedResult {

    public List<Double> time;

    // --- Control first (NEW ORDER) ---
    public List<Double> meanMu;
    public List<Double> stdMu;

    public List<Double> meanDeltaMu;
    public List<Double> stdDeltaMu;

    // --- State metrics ---
    public List<Double> meanEntropy;
    public List<Double> stdEntropy;

    public List<Double> meanCoherence;
    public List<Double> stdCoherence;
}

