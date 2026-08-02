/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;


import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;

import java.io.File;
import java.io.IOException;

/**
 *
 * @author veronique
 */

public class ChartExport {

    public static void saveAsPNG(
            JFreeChart chart, String path, int width, int height) {

        try {
            ChartUtils.saveChartAsPNG(
                    new File(path), chart, width, height
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
