/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 *
 * @author veronique
 */
public final class Csv {
    private Csv() {}

    public static void writeSeries(
            Path file,
            List<Double> x,
            List<Double> y,
            String xName,
            String yName
    ) throws IOException {
        ensureParentExists(file);
        try (BufferedWriter w = Files.newBufferedWriter(file)) {
            w.write(xName + "," + yName);
            w.newLine();

            int n = Math.min(x.size(), y.size());
            for (int i = 0; i < n; i++) {
                w.write(x.get(i) + "," + y.get(i));
                w.newLine();
            }
        }
    }

    public static void writeMeanStd(
            Path file,
            List<Double> t,
            List<Double> mean,
            List<Double> std,
            String xName,
            String baseName
    ) throws IOException {
        ensureParentExists(file);
        try (BufferedWriter w = Files.newBufferedWriter(file)) {
            w.write(xName + "," + baseName + "_mean," + baseName + "_std");
            w.newLine();

            int n = Math.min(Math.min(t.size(), mean.size()), std.size());
            for (int i = 0; i < n; i++) {
                w.write(t.get(i) + "," + mean.get(i) + "," + std.get(i));
                w.newLine();
            }
        }
    }

    public static void writeGrid(Path file, double[] grid) throws IOException {
        ensureParentExists(file);
        try (BufferedWriter w = Files.newBufferedWriter(file)) {
            for (double v : grid) {
                w.write(Double.toString(v));
                w.newLine();
            }
        }
    }

    /** Writes matrix rows as CSV lines. Assumes rectangular. */
    public static void writeMatrix(Path file, double[][] a) throws IOException {
        ensureParentExists(file);
        try (BufferedWriter w = Files.newBufferedWriter(file)) {
            for (double[] row : a) {
                for (int j = 0; j < row.length; j++) {
                    if (j > 0) {
                        w.write(",");
                    }
                    w.write(Double.toString(row[j]));
                }
                w.newLine();
            }
        }
    }

    /** Writes a generic table from header + rows. */
    public static void writeRows(Path file, String[] header, List<String[]> rows) throws IOException {
        ensureParentExists(file);
        try (BufferedWriter w = Files.newBufferedWriter(file)) {

            if (header != null && header.length > 0) {
                for (int i = 0; i < header.length; i++) {
                    if (i > 0) {
                        w.write(",");
                    }
                    w.write(escapeCsv(header[i]));
                }
                w.newLine();
            }

            if (rows != null) {
                for (String[] row : rows) {
                    if (row == null) {
                        continue;
                    }
                    for (int i = 0; i < row.length; i++) {
                        if (i > 0) {
                            w.write(",");
                        }
                        String cell = (row[i] == null) ? "" : row[i];
                        w.write(escapeCsv(cell));
                    }
                    w.newLine();
                }
            }
        }
    }

    private static void ensureParentExists(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static String escapeCsv(String s) {
        boolean needsQuotes =
                s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");

        if (!needsQuotes) {
            return s;
        }

        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}