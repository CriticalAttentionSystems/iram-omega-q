/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.noise;

/**
 *
 * @author veronique
 */
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CompositeNoiseModel implements NoiseModel {

    private final List<NoiseModel> models = new ArrayList<>();

    public CompositeNoiseModel(NoiseModel... models) {
        if (models == null || models.length == 0) {
            throw new IllegalArgumentException(
                    "At least one noise model is required");
        }

        for (NoiseModel model : models) {
            if (model == null) {
                throw new IllegalArgumentException(
                        "Noise models must not contain null entries");
            }
        }

        this.models.addAll(Arrays.asList(models));
    }
    

    @Override
    public void reset(long seed) {
        long s = seed;
        for (NoiseModel model : models) {
            model.reset(s);
            s = 31L * s + 17L;
        }
    }

    @Override
    public double noiseAt(NoiseContext ctx) {
        return NoiseModel.requireValidEta(
                nonInducedNoiseAt(ctx) + inducedNoiseAt(ctx));
    }
    
    
    @Override
    public double nonInducedNoiseAt(NoiseContext ctx) {
        double total = 0.0;

        for (NoiseModel model : models) {
            total += model.nonInducedNoiseAt(ctx);
        }

        return NoiseModel.requireValidEta(total);
    }

    @Override
    public double inducedNoiseAt(NoiseContext ctx) {
        double total = 0.0;

        for (NoiseModel model : models) {
            total += model.inducedNoiseAt(ctx);
        }

        return NoiseModel.requireValidEta(total);
    }
}