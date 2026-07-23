/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;
import java.util.Random;
import org.iram.omega.iram_omega_q.cognition.QuantumRegulationAgent.ControlOrdering;

/**
 *
 * @author veronique
 */
/**
 * Supplies a control ordering q(t) for each simulation step.
 *
 * <p>This class is deliberately outside QuantumRegulationAgent.  The RF and DF
 * update rules in QuantumRegulationAgent.step() remain unchanged; this class
 * changes only which existing rule is active at a particular time step.</p>
 *
 * <p>For stochastic schedules, a dedicated random stream is used.  It must not
 * share the disturbance RNG, otherwise adding a switching decision would also
 * change the noise realization and would confound paired RF/DF comparisons.</p>
 */
public final class OrderingSchedule {

    public enum Protocol {
        /** Use the single ordering specified by SimulationParameters.ordering. */
        FIXED,
        /** Alternate RF and DF in deterministic blocks of equal length. */
        PERIODIC,
        /** Use a two-state Markov chain with pLoss and pReturn. */
        MARKOV
    }

    private static final long DEFAULT_SWITCH_STREAM_SALT = 0x6A09E667F3BCC909L;

    private final Protocol protocol;
    private final ControlOrdering initialOrdering;
    private final int periodicDwellSteps;
    private final double pLoss;
    private final double pReturn;
    private final Random switchRng;

    private ControlOrdering current;
    private boolean initialized;
    private boolean switchedAtLastStep;

    public OrderingSchedule(SimulationParameters p) {
        if (p == null) {
            throw new IllegalArgumentException("parameters must not be null");
        }
        if (p.switchingProtocol == null) {
            throw new IllegalArgumentException("switchingProtocol must not be null");
        }
        if (p.ordering == null) {
            throw new IllegalArgumentException("initial ordering must not be null");
        }
        if (p.periodicDwellSteps < 1) {
            throw new IllegalArgumentException("periodicDwellSteps must be >= 1");
        }
        validateProbability(p.pLoss, "pLoss");
        validateProbability(p.pReturn, "pReturn");

        this.protocol = p.switchingProtocol;
        this.initialOrdering = p.ordering;
        this.periodicDwellSteps = p.periodicDwellSteps;
        this.pLoss = p.pLoss;
        this.pReturn = p.pReturn;

        long switchSeed = p.switchingSeed == Long.MIN_VALUE
                ? mix64(p.seed ^ DEFAULT_SWITCH_STREAM_SALT)
                : p.switchingSeed;
        this.switchRng = new Random(switchSeed);
        this.current = initialOrdering;
        this.initialized = false;
        this.switchedAtLastStep = false;
    }

    /**
     * Return q(t), the ordering to use at integration step {@code t}.
     */
    public ControlOrdering orderingAtStep(int t) {
        if (t < 0) {
            throw new IllegalArgumentException("t must be >= 0");
        }

        ControlOrdering previous = current;

        if (!initialized) {
            current = initialOrdering;
            initialized = true;
        } else if (protocol == Protocol.PERIODIC) {
            int block = t / periodicDwellSteps;
            current = (block % 2 == 0) ? initialOrdering : opposite(initialOrdering);
        } else if (protocol == Protocol.MARKOV) {
            if (current == ControlOrdering.REGULATION_FIRST) {
                if (switchRng.nextDouble() < pLoss) {
                    current = ControlOrdering.DISTURBANCE_FIRST;
                }
            } else if (switchRng.nextDouble() < pReturn) {
                current = ControlOrdering.REGULATION_FIRST;
            }
        } else {
            current = initialOrdering;
        }

        switchedAtLastStep = initialized && t > 0 && current != previous;
        return current;
    }

    public boolean switchedAtLastStep() {
        return switchedAtLastStep;
    }

    private static ControlOrdering opposite(ControlOrdering ordering) {
        return ordering == ControlOrdering.REGULATION_FIRST
                ? ControlOrdering.DISTURBANCE_FIRST
                : ControlOrdering.REGULATION_FIRST;
    }

    private static void validateProbability(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0, 1]");
        }
    }

    /** SplitMix-style avalanche used only to decorrelate deterministic streams. */
    private static long mix64(long x) {
        x ^= (x >>> 30);
        x *= 0xBF58476D1CE4E5B9L;
        x ^= (x >>> 27);
        x *= 0x94D049BB133111EBL;
        x ^= (x >>> 31);
        return x;
    }
}

