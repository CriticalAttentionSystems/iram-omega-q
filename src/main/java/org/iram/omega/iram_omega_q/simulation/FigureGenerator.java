/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.jfree.chart.ChartUtils;

/**
 *
 * @author veronique
 */

/**
 * Simulation figure generator for phase-transition and stability analysis.
 */
public class FigureGenerator {

    public static void figure1(
            AveragedResult r,
            int runs,
            File out) throws IOException {

        XYSeries eMean = series("SvN", r.time, r.meanEntropy);
        XYSeries eUp   = band("SvN+", r.time, r.meanEntropy, r.stdEntropy, runs, +1);
        XYSeries eLow  = band("SvN-", r.time, r.meanEntropy, r.stdEntropy, runs, -1);

        XYSeriesCollection data = new XYSeriesCollection();
        data.addSeries(eMean);
        data.addSeries(eUp);
        data.addSeries(eLow);

        JFreeChart chart =
            ChartFactory.createXYLineChart(
                "Figure 1A: Entropy Regulation",
                "Time",
                "SvN",
                data
            );

        ChartUtils.saveChartAsPNG(out, chart, 1200, 800);
    }

    private static XYSeries band(
            String name,
            List<Double> t,
            List<Double> m,
            List<Double> s,
            int N,
            int sign) {

        XYSeries series = new XYSeries(name);
        double z = 1.96 / Math.sqrt(N);

        for (int i = 0; i < t.size(); i++) {
            series.add(
                t.get(i).doubleValue(),
                m.get(i) + sign * z * s.get(i)
            );
        }
        return series;
    }
    
    private static XYSeries series(
        String name,
        List<Double> x,
        List<Double> y
    ) {
        XYSeries s = new XYSeries(name);
        for (int i = 0; i < x.size(); i++) {
            s.add(
                x.get(i).doubleValue(), y.get(i).doubleValue());
        }
        return s;
    }
    
    

}
