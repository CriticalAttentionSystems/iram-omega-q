/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.metrics;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author veronique
 */

/*
 * Entropy susceptibility is defined as the temporal variance of the system entropy 
 * and used as an operational indicator of criticality.
 */

public class EntropySusceptibilityTracker {

    private final List<Double> entropyHistory = new ArrayList<>();

    public void record(double entropy) {
        entropyHistory.add(entropy);
    }

    public double variance() {
        int n = entropyHistory.size();
        if (n < 2) return 0.0;

        double mean = entropyHistory.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        double var = 0.0;
        for (double s : entropyHistory) {
            double d = s - mean;
            var += d * d;
        }
        return var / (n - 1);
    }

    public void reset() {
        entropyHistory.clear();
    }
}
