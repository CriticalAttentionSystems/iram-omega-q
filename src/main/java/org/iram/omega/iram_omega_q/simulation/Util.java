/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;

import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/**
 *
 * @author veronique
 */
public class Util {
    
   public static long mixSeed(
           long base,
           int muIdx,
           int noiseIdx,
           int runIdx
   ) {
       long x = base;

       x ^= 0x9E3779B97F4A7C15L * (muIdx + 1L);
       x ^= 0xBF58476D1CE4E5B9L * (noiseIdx + 1L);
       x ^= 0x94D049BB133111EBL * (runIdx + 1L);

       // Final avalanche mixing.
       x ^= (x >>> 30);
       x *= 0xBF58476D1CE4E5B9L;

       x ^= (x >>> 27);
       x *= 0x94D049BB133111EBL;

       x ^= (x >>> 31);

       return x;
   }
    
    /* ===== helpers used sweep map ===== */

    public static double clamp01(double v) {
        return (v < 0) ? 0 : (v > 1) ? 1 : v;
    }

    public static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static double textWidth(String s, javafx.scene.text.Font font) {
        javafx.scene.text.Text t = new javafx.scene.text.Text(s);
        t.setFont(font);
        return t.getLayoutBounds().getWidth();
    }

    public static double textHeight(String s, javafx.scene.text.Font font) {
        javafx.scene.text.Text t = new javafx.scene.text.Text(s);
        t.setFont(font);
        return t.getLayoutBounds().getHeight();
    }
    
    public static boolean isUnicodeCapable(PDFont f) {
        return (f instanceof PDType0Font);
    }

    public static String asciiSafe(String s) {
        // Replace only the characters you actually use in UI/footer.
        return s
            .replace("μ", "mu")
            .replace("η", "eta")
            .replace("Δ", "d")
            .replace("⟨", "<")
            .replace("⟩", ">")
            .replace("−", "-");
    }
    
    public static double[] linspace(double a, double b, int n) {
        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = a + i * (b - a) / (n - 1);
        return x;
    }

}
