/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;

import org.iram.omega.iram_omega_q.cognition.QuantumRegulationAgent;

/**
 *
 * @author veronique
 */
public class SimulationParameters {

    public enum NoiseModelType {
        EXTERNAL,
        SELF_GENERATED,
        INDUCED,
        EXTERNAL_PLUS_SELF,
        EXTERNAL_PLUS_INDUCED,
        SELF_PLUS_INDUCED,
        MIXED
    }

    public NoiseModelType noiseModelType = NoiseModelType.EXTERNAL;

    public double selfNoiseRho = 0.98;
    public double selfNoiseSigma = 0.0;
    public double inducedNoiseAlpha = 0.0;
    public long noiseSeedOffset = 7919L;
    
    public enum ScheduleType {
        NONE,
        LINEAR,

        /**
         * Continuous up-and-down schedule used for a genuine hysteresis
         * protocol.  The state and controller are not reset at the turning
         * point, so the descending branch carries the history of the ascent.
         */
        TRIANGULAR
    }

    // seed
    public long baseSeed = 123456789L;   // set once, reproducible
    public long seed     = 0L;           // per-run seed

    // time
    public int steps = 10_000;
    public double dt = 0.01;

    // state
    public int dim = 16;
    public double salienceCenter = 6.0;
    public double salienceWidth = 2.0;
    public double phaseNoise = 0.2;

    /**
     * Basis-state index stabilized by the mindfulness operator.
     *
     * This is explicit so that time-series runs and phase sweeps apply the
     * same attentional focus.  Previously the two paths used indices 6 and 0.
     */
    public int focusIndex = 6;

    // Hamiltonian
    public double energyScale = 0.15;
    public double coupling = 0.08;
    public double locality = 2.0;

    // emotion + mindfulness
    public double emotionalNoise = 0.805;
    public double muInit = 0.25;
    public double muDerivativeGain = 5e-4;  // alpha0
    public double muTargetGain = 2e-4;      // beta0
    public double targetEntropy = 0.3;

    // control
    public QuantumRegulationAgent.ControlOrdering ordering =
            QuantumRegulationAgent.ControlOrdering.DISTURBANCE_FIRST;

    /**
     * Time-dependent control-ordering protocol.  FIXED reproduces the existing
     * RF-versus-DF experiments exactly.
     */
    public OrderingSchedule.Protocol switchingProtocol =
            OrderingSchedule.Protocol.FIXED;

    /** Number of integration steps per RF or DF block in PERIODIC mode. */
    public int periodicDwellSteps = 100;

    /** Markov transition probability P(RF -> DF) per integration step. */
    public double pLoss = 0.02;

    /** Markov transition probability P(DF -> RF) per integration step. */
    public double pReturn = 0.02;

    /**
     * Independent seed for ordering switching.  Long.MIN_VALUE means derive a
     * deterministic switching seed from the run seed.  It is kept separate
     * from the disturbance RNG to preserve paired noise histories.
     */
    public long switchingSeed = Long.MIN_VALUE;

    public double muMin = 1e-3;
    public double muMax = 1.0;

    // settling detector
    public double settleEps = 1e-4;
    public int settleWindow = 200;

    // intervention window
    public int interventionStart = -1;
    public int interventionEnd = -1;
    public double muLearningScaleDuringIntervention = 1.0;
    public double noiseMultiplierDuringIntervention = 1.0;
    public Double targetEntropyDuringIntervention = null;

    // reset / amnesia
    public int resetStateAtStep = -1;
    public int resetMuAtStep = -1;
    public boolean keepControllerHistoryOnReset = false;
    public boolean resetToInitialState = true;

    // slow schedules
    public double targetEntropyStart = Double.NaN;
    public double targetEntropyEnd = Double.NaN;
    public ScheduleType targetEntropySchedule = ScheduleType.NONE;

    // dwell-time / regime labeling
    public double focusThresholdLow = 0.05;
    public double focusThresholdHigh = 0.15;

    // hysteresis
    public boolean sweepUp = true;
    public boolean sweepDown = false;

    // metadata
    public String experimentTag = "baseline";

    public SimulationParameters copy() {
        SimulationParameters p = new SimulationParameters();

        // seed
        p.baseSeed = this.baseSeed;
        p.seed = this.seed;

        // time
        p.steps = this.steps;
        p.dt = this.dt;

        // state
        p.dim = this.dim;
        p.salienceCenter = this.salienceCenter;
        p.salienceWidth = this.salienceWidth;
        p.phaseNoise = this.phaseNoise;
        p.focusIndex = this.focusIndex;

        // Hamiltonian
        p.energyScale = this.energyScale;
        p.coupling = this.coupling;
        p.locality = this.locality;

        // emotion + mindfulness
        p.emotionalNoise = this.emotionalNoise;
        p.muInit = this.muInit;
        p.muTargetGain = this.muTargetGain;
        p.muDerivativeGain = this.muDerivativeGain;
        p.targetEntropy = this.targetEntropy;

        // control
        p.ordering = this.ordering;
        p.switchingProtocol = this.switchingProtocol;
        p.periodicDwellSteps = this.periodicDwellSteps;
        p.pLoss = this.pLoss;
        p.pReturn = this.pReturn;
        p.switchingSeed = this.switchingSeed;
        p.muMin = this.muMin;
        p.muMax = this.muMax;

        // experiment controls
        p.settleEps = this.settleEps;
        p.settleWindow = this.settleWindow;

        p.interventionStart = this.interventionStart;
        p.interventionEnd = this.interventionEnd;
        p.muLearningScaleDuringIntervention = this.muLearningScaleDuringIntervention;
        p.noiseMultiplierDuringIntervention = this.noiseMultiplierDuringIntervention;
        p.targetEntropyDuringIntervention = this.targetEntropyDuringIntervention;

        p.resetStateAtStep = this.resetStateAtStep;
        p.resetMuAtStep = this.resetMuAtStep;
        p.keepControllerHistoryOnReset = this.keepControllerHistoryOnReset;
        p.resetToInitialState = this.resetToInitialState;

        p.targetEntropyStart = this.targetEntropyStart;
        p.targetEntropyEnd = this.targetEntropyEnd;
        p.targetEntropySchedule = this.targetEntropySchedule;

        p.focusThresholdLow = this.focusThresholdLow;
        p.focusThresholdHigh = this.focusThresholdHigh;

        p.sweepUp = this.sweepUp;
        p.sweepDown = this.sweepDown;

        p.experimentTag = this.experimentTag;

        p.noiseModelType = this.noiseModelType;
        p.selfNoiseRho = this.selfNoiseRho;
        p.selfNoiseSigma = this.selfNoiseSigma;
        p.inducedNoiseAlpha = this.inducedNoiseAlpha;
        p.noiseSeedOffset = this.noiseSeedOffset;
        
        return p;
    }
}