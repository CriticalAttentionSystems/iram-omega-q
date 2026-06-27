/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;

import java.nio.file.Path;

/**
 *
 * @author veronique
 */

public class RunFromConfig {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: RunFromConfig <path-to-properties>"
            );
        }
        System.out.println("ARGS = " + java.util.Arrays.toString(args));
        Path path = Path.of(args[0]);
        RunConfig cfg = RunConfig.load(path);
        System.out.println("Loading config from: " + java.nio.file.Paths.get(args[0]).toAbsolutePath());
        SimulationRunner.run(cfg);
    }
}