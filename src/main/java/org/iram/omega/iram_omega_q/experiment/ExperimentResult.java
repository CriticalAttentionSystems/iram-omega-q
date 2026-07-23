/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.experiment;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author veronique
 */


public class ExperimentResult {

    private static class Row {
        int t;
        double SvN, Sdiag, coherence, mu;
    }

    private final List<Row> rows = new ArrayList<>();

    public void record(
        int t,
        double SvN,
        double Sdiag,
        double coherence,
        double mu
    ) {
        Row r = new Row();
        r.t = t;
        r.SvN = SvN;
        r.Sdiag = Sdiag;
        r.coherence = coherence;
        r.mu = mu;
        rows.add(r);
    }

    public void printSummary() {
        Row last = rows.get(rows.size() - 1);
        System.out.println("Final state:");
        System.out.println("SvN        = " + last.SvN);
        System.out.println("Sdiag      = " + last.Sdiag);
        System.out.println("Coherence  = " + last.coherence);
        System.out.println("Mindfulness μ = " + last.mu);
    }

    public List<Row> rows() {
        return rows;
    }
}
