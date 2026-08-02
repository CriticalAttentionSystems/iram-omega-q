/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.metrics;

/**
 *
 * @author veronique
 */
import java.io.*;
import java.util.*;

public class MetricsLoader {

     
    public static List<MetricRow> load(String path) {

        List<MetricRow> rows = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(path))) {

            String line = br.readLine(); // skip header
            if (line == null)
                throw new IllegalStateException("Metrics file is empty");

            while ((line = br.readLine()) != null) {
                String[] t = line.split(",");
                
                if (t.length != 10) {
                    throw new IllegalStateException(
                        "Invalid metrics row (expected 10 columns): " + line
                    );
                }

                MetricRow row = new MetricRow(
                        Integer.parseInt(t[1]),             // timestep,
                        t[2],                               // agentId,
                        Integer.parseInt(t[3]),             // agentCount,
                        Double.parseDouble(t[4]),           // integrity,
                        Double.parseDouble(t[5]),           // awareness,
                        Double.parseDouble(t[6]),           // emotionalActivity,
                        Double.parseDouble(t[7]),           // reputation,
                        t[8],                               // regime,
                        Boolean.parseBoolean(t[9].trim())   // collapsed
                );

                rows.add(row);
            }

        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to load metrics from " + path, e
            );
        }

        return rows;
    }
}
