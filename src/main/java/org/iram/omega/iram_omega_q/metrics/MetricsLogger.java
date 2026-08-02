/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.metrics;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 *
 * @author veronique
 */

public class MetricsLogger {

    private final PrintWriter out;

    public void log(
        int step,
        int timestep,
        String agentId,
        int agentCount,
        double integrity,
        double awareness,
        double emotionalActivity,
        double reputation,
        String regime,
        boolean collapsed
    ) {
        out.printf(
            "%d,%d,%s,%d,%.6f,%.6f,%.6f,%.6f,%s,%b%n",
            step,
            timestep,
            agentId,
            agentCount,
            integrity,
            awareness,
            emotionalActivity,
            reputation,
            regime,
            collapsed
        );
    }
    
    public MetricsLogger(String filename) {
        try {
            out = new PrintWriter(new FileWriter(filename));
            out.println(
                    "step," +
                    "timestep," +
                    "agentId," +
                    "agentCount," +
                    "integrity," +
                    "awareness," +
                    "emotionalActivity," +
                    "reputation," +
                    "regime," +
                    "collapsed"
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to open metrics file", e);
        }
    }

    
    public void close() {
        out.flush();
        out.close();
    }
}



