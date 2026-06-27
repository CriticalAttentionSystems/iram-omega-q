/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author veronique
 */

public final class Grid {
    private Grid() {}

    /**
     * Parses either:
     *  - "start:end:n" (linspace inclusive)
     *  - "v1,v2,v3" (explicit)
     */
    public static double[] parse(String spec) {
        spec = spec.trim();
        if (spec.contains(":")) {
            String[] parts = spec.split(":");
            if (parts.length != 3) throw new IllegalArgumentException("Grid must be start:end:n, got: " + spec);
            double a = Double.parseDouble(parts[0].trim());
            double b = Double.parseDouble(parts[1].trim());
            int n = Integer.parseInt(parts[2].trim());
            return linspace(a, b, n);
        }

        if (spec.contains(",")) {
            String[] parts = spec.split(",");
            List<Double> vals = new ArrayList<>();
            for (String s : parts) {
                String t = s.trim();
                if (!t.isEmpty()) vals.add(Double.parseDouble(t));
            }
            double[] out = new double[vals.size()];
            for (int i = 0; i < vals.size(); i++) out[i] = vals.get(i);
            return out;
        }

        // single value
        return new double[]{ Double.parseDouble(spec) };
    }

    public static double[] linspace(double a, double b, int n) {
        if (n <= 1) return new double[]{ a };
        double[] x = new double[n];
        double step = (b - a) / (n - 1);
        for (int i = 0; i < n; i++) x[i] = a + i * step;
        return x;
    }
}