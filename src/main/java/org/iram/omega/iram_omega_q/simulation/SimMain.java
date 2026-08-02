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

public class SimMain {
    public static void main(String[] args) throws Exception {
        String configPath = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--config") && i + 1 < args.length) configPath = args[i + 1];
        }
        if (configPath == null) {
            System.err.println("Usage: java -jar iram-omega-sim.jar --config <file.properties>");
            System.exit(2);
        }

        RunConfig cfg = RunConfig.load(Path.of(configPath));
        cfg.save(Path.of("configs/gui_last.properties"));
        // IMPORTANT: call the same core simulation that GUI uses
        SimulationRunner.run(cfg);
    }
}