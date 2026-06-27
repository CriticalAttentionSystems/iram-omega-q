/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.iram.omega.iram_omega_q.simulation;

import org.iram.omega.iram_omega_q.cognition.QuantumConsciousAgent;
import org.iram.omega.iram_omega_q.simulation.ConsciousSimulation.SimulationResult;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;

import static javafx.application.Application.launch;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.Arrays;
import java.util.List;

// PDF export deps 
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ScrollPane;
import javax.imageio.ImageIO;
import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.batik.svggen.SVGGraphics2DIOException;
import static org.iram.omega.iram_omega_q.simulation.Util.asciiSafe;
import static org.iram.omega.iram_omega_q.simulation.Util.clamp;
import static org.iram.omega.iram_omega_q.simulation.Util.clamp01;
import static org.iram.omega.iram_omega_q.simulation.Util.isUnicodeCapable;
import static org.iram.omega.iram_omega_q.simulation.Util.linspace;
import static org.iram.omega.iram_omega_q.simulation.Util.textHeight;
import static org.iram.omega.iram_omega_q.simulation.Util.textWidth;
import org.w3c.dom.Document;

/**
 *
 * @author veronique
 */
/**
 * Full experimental GUI for IRAM-ΩΣΞ simulations.
 *
 * <p>
 * The interface supports two distinct analysis modes:
 * </p>
 *
 * <ul>
 *   <li><b>Exploratory mode</b> prioritizes rapid qualitative insight and
 *       interactive hypothesis generation. Sweeps compute simple time-averaged
 *       observables (e.g., entropy or coherence) without enforcing burn-in,
 *       susceptibility estimation, or critical-point detection.</li>
 *
 *   <li><b>Publication mode</b> enforces statistically controlled analysis suitable
 *       for figures and results reported in manuscripts. Parameter sliders are
 *       locked, sweeps include burn-in and ensemble averaging, susceptibility is
 *       computed, and critical μ values are detected and optionally overlaid.</li>
 * </ul>
 *
 * <p>
 * This separation ensures that exploratory visualization does not contaminate
 * publication-quality results while preserving a shared execution pathway.
 * </p>
 */


public class ConsciousSimulationApp extends Application {

    private final SimulationParameters params = new SimulationParameters();

    private enum SweepMode { EXPLORATORY, PUBLICATION }
    private SweepMode sweepMode = SweepMode.EXPLORATORY;

    /* === Charts === */
    private AreaChart<Number, Number> entropyChart;
    private AreaChart<Number, Number> coherenceChart;
    private AreaChart<Number, Number> muChart;
    private AreaChart<Number, Number> dmuChart;

    private XYChart.Series<Number, Number> muZeroLine;
    private XYChart.Series<Number, Number> dmuZeroLine;

    private ScrollPane plotsScroller;
    private VBox plotsRoot;
    
    /* === Phase diagram === */
    private Canvas heatmap;
    private Label heatmapProbe;
    private CheckBox showCriticalMu;

    private SimulationResult last;
    private AveragedResult lastAvg;
    private double[][] lastHeatmap;
    private double[] heatmapMu, heatmapNoise;

    private double[][] lastSusceptibility;
    private double[] lastMuCritical;
    private double lastMuCBar = Double.NaN;
    private double lastMuCSigma = Double.NaN;
    
    /* === Sliders === */
    private Slider emotionalNoise;
    private Slider targetEntropy;
    private Slider muSlider; 
    private CheckBox quickSweep;
    
    /* ===================================================== */

    @Override
    public void start(Stage stage) {
        System.out.println("🔥 RUNNING ConsciousSimulationApp — PUB/EXP MODE ENABLED");

        BorderPane root = new BorderPane();
        root.setLeft(controls(stage));
        //root.setCenter(plots());
        ScrollPane scroller = new ScrollPane(plots());
        scroller.setFitToWidth(true);
        scroller.setFitToHeight(false);
        scroller.setPannable(true);
        scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroller.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        plotsRoot = plots();                 // VBox returned by your plots() builder
        plotsScroller = new ScrollPane(plotsRoot);
        plotsScroller.setFitToWidth(true);

        root.setCenter(plotsScroller);
        //root.setCenter(scroller);

        Scene scene = new Scene(root, 1500, 950);
        stage.setTitle("Quantum Consciousness Simulator");
        stage.setScene(scene);
        stage.show();
    }

    /* ================= CONTROLS ================= */

    private VBox controls(Stage stage) {
        Label sweepStatus = new Label("Idle");
        ProgressBar sweepProgress = new ProgressBar(0);
        sweepProgress.setPrefWidth(180);
        //params.baseSeed = 123456789L;//test
        quickSweep = new CheckBox("Quick sweep (fast, lower resolution)");
        quickSweep.setSelected(false);

        showCriticalMu = new CheckBox("Show μ(c) (susceptibility peak)");
        showCriticalMu.setSelected(false);
        showCriticalMu.setDisable(true); // enabled only in publication mode

        ComboBox<SweepMode> modeBox = new ComboBox<>();
        modeBox.getItems().addAll(SweepMode.EXPLORATORY, SweepMode.PUBLICATION);
        modeBox.setValue(SweepMode.EXPLORATORY);

        // Sliders
        Label muLabel = new Label("μ");
        Label muValue = new Label();
        muSlider = new Slider(0, 1, params.muInit);
        muValue.textProperty().bind(muSlider.valueProperty().asString("%.3f"));
        muSlider.valueProperty().addListener((obs, oldV, newV) -> params.muInit = newV.doubleValue());
        HBox muRow = new HBox(8, muLabel, muSlider, muValue);
        muRow.setAlignment(Pos.CENTER_LEFT);

        Label entLabel = new Label("S");
        Label entValue = new Label();
        targetEntropy = new Slider(0, 1, params.targetEntropy);
        entValue.textProperty().bind(targetEntropy.valueProperty().asString("%.3f"));
        targetEntropy.valueProperty().addListener((obs, oldV, newV) -> params.targetEntropy = newV.doubleValue());
        HBox entRow = new HBox(8, entLabel, targetEntropy, entValue);
        entRow.setAlignment(Pos.CENTER_LEFT);

        Label noiseLabel = new Label("η");
        Label noiseValue = new Label();
        emotionalNoise = new Slider(0, 1, params.emotionalNoise);
        noiseValue.textProperty().bind(emotionalNoise.valueProperty().asString("%.3f"));
        emotionalNoise.valueProperty().addListener((obs, oldV, newV) -> params.emotionalNoise = newV.doubleValue());
        HBox noiseRow = new HBox(8, noiseLabel, emotionalNoise, noiseValue);
        noiseRow.setAlignment(Pos.CENTER_LEFT);

        // Ordering selector
        ComboBox<QuantumConsciousAgent.ControlOrdering> ordering = new ComboBox<>();
        ordering.getItems().addAll(QuantumConsciousAgent.ControlOrdering.values());
        ordering.setValue(params.ordering);
        ordering.setOnAction(e -> params.ordering = ordering.getValue());

        // sweep
        modeBox.setOnAction(e -> {
            sweepMode = modeBox.getValue();
            boolean pub = (sweepMode == SweepMode.PUBLICATION);

            setPublicationLock(pub);

            showCriticalMu.setDisable(!pub);

            if (!pub) {
                showCriticalMu.setSelected(false);
                lastMuCritical = null;
                drawHeatmap(); // optional: clears overlay
                return;
            }

            // pub == true
            // If we already have susceptibility from a previous pub sweep, allow re-render
            if (lastSusceptibility != null && heatmapMu != null) {
                lastMuCritical = showCriticalMu.isSelected()
                        ? PhaseDiagramSweep.detectCriticalMu(heatmapMu, lastSusceptibility)
                        : null;

                drawHeatmap();
            }

            // Only compute "edge hits" if we actually have muCritical AND a valid mu grid
            if (lastMuCritical != null && heatmapMu != null && heatmapMu.length >= 2) {
                int edgeHits = 0;
                double muMin = heatmapMu[0];
                double muMax = heatmapMu[heatmapMu.length - 1];

                for (double v : lastMuCritical) {
                    if (!Double.isFinite(v)) continue;
                    if (v == muMin || v == muMax) edgeHits++;
                }
                System.out.println("μc edge-hits = " + edgeHits + " / " + lastMuCritical.length);
            } else {
                System.out.println("μc edge-hits = (skipped; no μc curve yet)");
            }
        });

        Button run = new Button("Run single");
        run.setOnAction(e -> {
            last = ConsciousSimulation.run(params);

            plotMean(entropyChart,   last.time, last.entropy,   "SvN");
            plotMean(coherenceChart, last.time, last.coherence, "ΔC");
            plotMean(muChart,        last.time, last.mu,        "μ");
            plotMean(dmuChart,       last.time, last.deltaMu,   "Δμ");
        });

        Button avg = new Button("Run averaged");
        avg.setOnAction(e -> {
            avg.setDisable(true);

            Task<AveragedResult> task = new Task<>() {
                @Override protected AveragedResult call() {
                    return ConsciousSimulation.runAveraged(params, 30, 200);
                }
            };

            task.setOnSucceeded(ev -> {
                lastAvg = task.getValue();

                plotCI(entropyChart,   lastAvg.time, lastAvg.meanEntropy,   lastAvg.stdEntropy,   "SvN entropy");
                plotCI(coherenceChart, lastAvg.time, lastAvg.meanCoherence, lastAvg.stdCoherence, "Coherence gap ΔC");
                plotCI(muChart,        lastAvg.time, lastAvg.meanMu,        lastAvg.stdMu,        "μ(t)");
                plotCI(dmuChart,       lastAvg.time, lastAvg.meanDeltaMu,   lastAvg.stdDeltaMu,   "Δμ(t)");
                

                // ===== steady-state summary over final window =====
                int n = lastAvg.meanMu.size();
                int W = Math.max(10, n / 5);          // last 20% (min 10 points)
                int i0 = n - W;

                double muInf = 0.0, sigInf = 0.0;
                for (int i = i0; i < n; i++) {
                    muInf  += lastAvg.meanMu.get(i);
                    sigInf += lastAvg.stdMu.get(i);
                }
                muInf  /= W;
                sigInf /= W;

                System.out.printf("STEADY: mu_inf=%.6f  sigma_mu=%.6f  (W=%d of n=%d)%n",
                        muInf, sigInf, W, n);

                // Optional: same for Δμ mean magnitude
                double dmuAbs = 0.0;
                for (int i = i0; i < n; i++) dmuAbs += Math.abs(lastAvg.meanDeltaMu.get(i));
                dmuAbs /= W;

                System.out.printf("STEADY: mean|Δμ|=%.6e%n", dmuAbs);

                avg.setDisable(false);
            });

            task.setOnFailed(ev -> {
                if (task.getException() != null) task.getException().printStackTrace();
                avg.setDisable(false);
            });

            Thread t = new Thread(task);
            t.setDaemon(true);
            t.start();
        });

        Button sweep = new Button("μ–noise sweep");
        sweep.setOnAction(e -> {

            sweep.setDisable(true);

            boolean fast = quickSweep.isSelected();

            double[] muGrid    = linspace(0.05, 1.0, fast ? 20 : 60);
            double[] noiseGrid = linspace(1e-4, 0.30, fast ? 20 : 40);

            if (sweepMode == SweepMode.PUBLICATION) {
                runPublicationSweep(
                    muGrid, noiseGrid, sweep, sweepStatus, sweepProgress, fast
                );
            } else {
                runExploratorySweep(
                    muGrid, noiseGrid, sweep, sweepStatus, sweepProgress
                );
            }
        });
        Button pdf = new Button("Export PDF");
        pdf.setOnAction(e -> exportPDF(stage));

        Button png = new Button("Export PNG");
        png.setOnAction((ActionEvent e) -> {
            exportPNG(stage);
        });

        Button svg = new Button("Export SVG");
        svg.setOnAction(e -> {
            try {
                exportSVG(stage);
            } catch (SVGGraphics2DIOException ex) {
                ex.printStackTrace();
            }
        });
        
        VBox box = new VBox(12);
        box.setPadding(new Insets(10));
        box.getChildren().addAll(sweepStatus,
            sweepProgress,
            new Label("Analysis mode"),
            modeBox,

            noiseRow,
            entRow,
            muRow,

            new Label("Control ordering"),
            ordering,

            run,
            avg,
            quickSweep,
            sweep,
            showCriticalMu,
            pdf,
            png,
            svg
        );

        // Recompute critical μ line when user toggles checkbox
        showCriticalMu.setOnAction(e -> {
            if (lastSusceptibility == null || heatmapMu == null) return;
            lastMuCritical = showCriticalMu.isSelected()
                ? PhaseDiagramSweep.detectCriticalMu(heatmapMu, lastSusceptibility)
                : null;
            
            drawHeatmap();
        });

        return box;
    }

    /* ===== SWEEPS ======*/
    private void debugSusceptibilitySlices(double[] mu, double[] noise, double[][] chi) {
        if (mu == null || noise == null || chi == null) return;

        int r = chi.length, c = chi[0].length;
        System.out.println("χ dims = " + r + " x " + c + "   mu=" + mu.length + "   noise=" + noise.length);

        // pick a representative noise index: closest to current slider noise
        int jStar = 0;
        double bestDist = Double.POSITIVE_INFINITY;
        for (int j = 0; j < noise.length; j++) {
            double d = Math.abs(noise[j] - params.emotionalNoise);
            if (d < bestDist) { bestDist = d; jStar = j; }
        }

        // Determine orientation the same way detectCriticalMu does
        boolean muIsRows;
        if (r == mu.length) muIsRows = true;
        else if (c == mu.length) muIsRows = false;
        else {
            System.out.println("Cannot match mu length to χ dims.");
            return;
        }

        // Print χ(μ, η*) across μ so you can see if it’s monotone / broken
        System.out.println("Slice at noise[" + jStar + "]=" + noise[jStar] + " (ordering=" + params.ordering + ")");
        for (int i = 0; i < mu.length; i++) {
            double v = muIsRows ? chi[i][jStar] : chi[jStar][i];
            System.out.printf("  mu=%.3f  chi=%.6g%n", mu[i], v);
        }

        // Also report argmax for that slice
        int bestI = -1;
        double best = -Double.MAX_VALUE;
        for (int i = 0; i < mu.length; i++) {
            double v = muIsRows ? chi[i][jStar] : chi[jStar][i];
            if (Double.isFinite(v) && v > best) { best = v; bestI = i; }
        }
        System.out.println("Argmax at mu=" + (bestI >= 0 ? mu[bestI] : Double.NaN) + " chi=" + best);
    }
    
    private void runExploratorySweep(
        double[] muGrid,
        double[] noiseGrid,
        Button sweep,
        Label sweepStatus,
        ProgressBar sweepProgress
    ) {
        Task<double[][]> task = new Task<>() {
            @Override protected double[][] call() {
                int total = muGrid.length * noiseGrid.length;
                int done  = 0;

                long startTime = System.nanoTime();

                // heatmap[x=mu][y=noise]
                double[][] heatmap = new double[muGrid.length][noiseGrid.length];

                for (int i = 0; i < muGrid.length; i++) {
                    for (int j = 0; j < noiseGrid.length; j++) {

                        if (isCancelled()) return null;

                        double mu    = muGrid[i];
                        double noise = noiseGrid[j];

                        double sum = 0.0;

                        // EXPLORATORY: use coherence gap average (matches UI semantics)
                        for (int r = 0; r < 15; r++) {
                            SimulationParameters p = params.copy();
                            p.muInit = mu;
                            p.emotionalNoise = noise;
                            p.seed = Util.mixSeed(
                                p.baseSeed,
                                i,                  // mu index
                                j,                  // noise index
                                r                  // run index
                            );
                            SimulationResult rs = ConsciousSimulation.run(p);

                            sum += rs.coherence.stream()
                                .mapToDouble(Double::doubleValue)
                                .average()
                                .orElse(0.0);
                        }

                        heatmap[i][j] = sum / 15.0;

                        done++;
                        updateProgress(done, total);

                        long elapsedNs = System.nanoTime() - startTime;
                        double avgNsPerStep = (double) elapsedNs / Math.max(1, done);
                        long remainingNs = (long) ((total - done) * avgNsPerStep);

                        long sec = remainingNs / 1_000_000_000;
                        updateMessage(String.format(
                            "μ=%.2f noise=%.3f — ~%d:%02d remaining",
                            mu, noise, sec / 60, sec % 60
                        ));
                    }
                }
                return heatmap;
            }
        };

        sweepStatus.textProperty().bind(task.messageProperty());
        sweepProgress.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(ev -> {
            lastHeatmap  = task.getValue();
            heatmapMu    = muGrid;
            heatmapNoise = noiseGrid;

            lastMuCritical     = null;
            lastSusceptibility = null;

            sweepStatus.textProperty().unbind();
            sweepProgress.progressProperty().unbind();
            sweepStatus.setText("Sweep complete");

            drawHeatmap();
            sweep.setDisable(false);
        });

        task.setOnFailed(ev -> {
            if (task.getException() != null) task.getException().printStackTrace();
            sweepStatus.textProperty().unbind();
            sweepProgress.progressProperty().unbind();
            sweepStatus.setText("Sweep failed");
            sweep.setDisable(false);
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void runPublicationSweep(
        double[] muGrid,
        double[] noiseGrid,
        Button sweep,
        Label sweepStatus,
        ProgressBar sweepProgress,
        boolean fast
    ) {
        SimulationParameters sweepParams = params.copy();
        int steps = fast ? 4000 : 20_000;   
        sweepParams.steps = steps;

        int burnIn = fast ? 400 : Math.max(800, steps / 20);
        int runsPerPoint = fast ? 5 : 20;
     
        Task<PhaseDiagramResult> task = new Task<>() {
            @Override protected PhaseDiagramResult call() {
                return PhaseDiagramSweep.run(
                    sweepParams,
                    noiseGrid,
                    muGrid,
                    burnIn,   
                    runsPerPoint   
                );
            }
        };

        sweepStatus.setText("Running publication sweep…");
        
        sweepStatus.textProperty().bind(task.messageProperty());
        sweepProgress.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(ev -> {
            PhaseDiagramResult result = task.getValue();

            lastHeatmap        = result.meanCoherence;
            lastSusceptibility = result.susceptibility;
            heatmapMu          = result.muValues;
            heatmapNoise       = result.noiseValues;

            // Normalize dims to [mu][noise]
            lastHeatmap        = ensureMuNoiseShape(lastHeatmap, heatmapMu.length, heatmapNoise.length, "meanCoherence");
            lastSusceptibility = ensureMuNoiseShape(lastSusceptibility, heatmapMu.length, heatmapNoise.length, "susceptibility");

            if (lastHeatmap.length != heatmapMu.length ||
                lastHeatmap[0].length != heatmapNoise.length) {
                throw new IllegalStateException("Heatmap shape mismatch after normalization");
            }

            if (lastSusceptibility.length != heatmapMu.length ||
                lastSusceptibility[0].length != heatmapNoise.length) {
                throw new IllegalStateException("Susceptibility shape mismatch after normalization");
            }
            
            debugSusceptibilitySlices(heatmapMu, heatmapNoise, lastSusceptibility);
            lastMuCritical = (lastSusceptibility != null)
                    ? PhaseDiagramSweep.detectCriticalMu(heatmapMu, lastSusceptibility)
                    : null;
            // ===== DETERMINISM CHECK =====
            // Determinism checksum (prints the same number if fully deterministic)
            if (lastMuCritical != null) {
                System.out.println("CHECKSUM muCritical = " + Arrays.hashCode(lastMuCritical));
            }
            if (lastHeatmap != null) {
                System.out.println("CHECKSUM heatmap    = " + Arrays.deepHashCode(lastHeatmap));
            }
            if (lastMuCritical != null) {
                long hash = 1;
                for (double v : lastMuCritical) {
                    if (Double.isFinite(v)) {
                        hash = 31 * hash + Double.doubleToLongBits(v);
                    }
                }
                System.out.println("μc checksum = " + hash);
            }
            // compute μ̄c ± σμc consistently from lastMuCritical (ignore NaNs)
            if (lastMuCritical != null) {
                MuStats stats = MuStats.from(lastMuCritical, heatmapMu[0], heatmapMu[heatmapMu.length - 1]);
                lastMuCBar   = stats.mean;
                lastMuCSigma = stats.std;
                System.out.println("MuStats used n=" + stats.nUsed + " / " + stats.nTotal);
            } else {
                lastMuCBar = Double.NaN;
                lastMuCSigma = Double.NaN;
            }
            
            sweepStatus.textProperty().unbind();
            sweepProgress.progressProperty().unbind();
            sweepStatus.setText("Publication sweep complete");

            drawHeatmap();
            sweepProgress.setProgress(0);
            sweep.setDisable(false);
            
            System.out.printf("μ̄c = %.4f  σμc = %.4f%n", lastMuCBar, lastMuCSigma);
            System.out.println("muLen=" + heatmapMu.length + " noiseLen=" + heatmapNoise.length);
            System.out.println("meanCoherence dims=" + lastHeatmap.length + "x" + lastHeatmap[0].length);
            System.out.println("susceptibility dims=" + lastSusceptibility.length + "x" + lastSusceptibility[0].length);
        });

        task.setOnFailed(ev -> {
            if (task.getException() != null) task.getException().printStackTrace();
            sweepStatus.textProperty().unbind();
            sweepProgress.progressProperty().unbind();
            sweepStatus.setText("Sweep failed");
            sweepProgress.setProgress(0);
            sweep.setDisable(false);
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private static double[][] ensureMuNoiseShape(double[][] a, int muLen, int noiseLen, String name) {
        if (a == null || a.length == 0 || a[0].length == 0) return a;

        int r = a.length;
        int c = a[0].length;

        // already correct: [mu][noise]
        if (r == muLen && c == noiseLen) return a;

        // transposed: [noise][mu] -> transpose to [mu][noise]
        if (r == noiseLen && c == muLen) {
            System.out.println("⚠️ Transposing " + name + " from [noise][mu] to [mu][noise]");
            double[][] t = new double[c][r];
            for (int i = 0; i < r; i++) {
                for (int j = 0; j < c; j++) {
                    t[j][i] = a[i][j];
                }
            }
            return t;
        }

        System.out.println("⚠️ Unexpected " + name + " dims: " + r + "x" + c +
                " expected " + muLen + "x" + noiseLen + " (or transpose). Using as-is.");
        for (int i = 1; i < a.length; i++) {
            if (a[i].length != a[0].length) {
                System.out.println("⚠️ Ragged " + name + " matrix at row " + i);
                return a;
            }
        }
        return a;
    }
    /* ================= PLOTS ================= */

    private VBox plots() {
        entropyChart   = areaChart("SvN entropy");
        coherenceChart = areaChart("Coherence gap ΔC");
        muChart        = areaChart("Regulation gain μ(t)");
        dmuChart        = areaChart("Adaptive control update Δμ(t)");

        muZeroLine = zeroLineSeries(110);
        dmuZeroLine = zeroLineSeries(110);
        dmuChart.getData().add(dmuZeroLine);
        muChart.getData().add(muZeroLine);

        Platform.runLater(() -> {
            styleZeroLine(muZeroLine);
            styleZeroLine(dmuZeroLine);
        });

        StackPane dmuPane = new StackPane(dmuChart);
        Label annotation = regulationAnnotation();
        StackPane.setAlignment(annotation, Pos.TOP_RIGHT);
        StackPane.setMargin(annotation, new Insets(10));
        dmuPane.getChildren().add(annotation);

        heatmap = new Canvas(520, 300);
        heatmapProbe = new Label("click heatmap");
        heatmap.addEventHandler(MouseEvent.MOUSE_CLICKED, this::probeHeatmap);

        VBox box = new VBox(12,
            entropyChart,
            coherenceChart,
            muChart,    
            dmuPane,
            new Label("Phase diagram (μ vs noise η)"),
            heatmap,
            heatmapProbe
        );
        box.setPadding(new Insets(10));
        return box;
    }

    private void styleZeroLine(XYChart.Series<Number, Number> s) {
        styleZeroLine(s, 0);
    }
    private void styleZeroLine(XYChart.Series<Number, Number> s, int tries) {
        if (tries > 120) return; // ~2 seconds @ 60fps pulses
        Platform.runLater(() -> {
            Node node = s.getNode();
            if (node == null) {
                styleZeroLine(s, tries + 1);
                return;
            }
            Node line = node.lookup(".chart-series-line");
            if (line != null) {
                line.setStyle("""
                    -fx-stroke: derive(gray, -30%);
                    -fx-stroke-width: 1.2px;
                    -fx-stroke-dash-array: 6 6;
                """);
            }
        });
    }
    
    private AreaChart<Number, Number> areaChart(String title) {
        NumberAxis x = new NumberAxis();
        NumberAxis y = new NumberAxis();
        AreaChart<Number, Number> c = new AreaChart<>(x, y);
        c.setTitle(title);
        c.setCreateSymbols(false);
        return c;
    }

    /* ================= CI PLOTTING ================= */

    private void plotMean(AreaChart<Number, Number> chart,
                          List<Double> x, List<Double> y,
                          String name) {

        XYChart.Series<Number, Number> s = new XYChart.Series<>();
        s.setName(name);

        int n = Math.min(x.size(), y.size());
        for (int i = 0; i < n; i++) {
            s.getData().add(new XYChart.Data<>(x.get(i), y.get(i)));
        }
        chart.getData().setAll(s);
    }

    /** Shaded ±σ envelope                               */
    /** Mean line with shaded ±σ band (JavaFX AreaChart) */
    private void plotCI(
            AreaChart<Number, Number> chart,
            List<Double> t,
            List<Double> mean,
            List<Double> std,
            String name) {

        int n = Math.min(Math.min(t.size(), mean.size()), std.size());

        // 1) Mean series (this is the line readers should track)
        XYChart.Series<Number, Number> meanSeries = new XYChart.Series<>();
        meanSeries.setName(name);

        // 2) Upper/lower series to form the filled band
        XYChart.Series<Number, Number> upper = new XYChart.Series<>();
        XYChart.Series<Number, Number> lower = new XYChart.Series<>();
        upper.setName(name + " +σ");
        lower.setName(name + " −σ");

        for (int i = 0; i < n; i++) {
            double ti = t.get(i);
            double m  = mean.get(i);
            double s  = std.get(i);

            meanSeries.getData().add(new XYChart.Data<>(ti, m));
            upper.getData().add(new XYChart.Data<>(ti, m + s));
            lower.getData().add(new XYChart.Data<>(ti, m - s));
        }

        // Order matters:
        // Put the band series first so its fill is behind the mean line.
        chart.getData().setAll(lower, upper, meanSeries);

        // 3) Style after nodes exist
        Platform.runLater(() -> {
            // --- BAND: make fill translucent ---
            // One fill node per series; we want the band fill to be visible but light.
            for (Node fill : chart.lookupAll(".chart-series-area-fill")) {
                fill.setStyle("-fx-opacity: 0.20;");  // tweak 0.15–0.30
            }

            // --- BAND: hide the boundary lines (upper/lower) ---
            // This prevents the “two-line contour” look.
            Node lowerLine = lower.getNode() != null ? lower.getNode().lookup(".chart-series-line") : null;
            Node upperLine = upper.getNode() != null ? upper.getNode().lookup(".chart-series-line") : null;
            if (lowerLine != null) lowerLine.setStyle("-fx-stroke-width: 0px; -fx-opacity: 0;");
            if (upperLine != null) upperLine.setStyle("-fx-stroke-width: 0px; -fx-opacity: 0;");

            // --- MEAN: emphasize mean line ---
            Node meanLine = meanSeries.getNode() != null ? meanSeries.getNode().lookup(".chart-series-line") : null;
            if (meanLine != null) {
                meanLine.setStyle("-fx-stroke-width: 2.0px; -fx-opacity: 1.0;");
            }

            // Optional: remove symbols if they appear
            for (Node symbol : chart.lookupAll(".chart-line-symbol")) {
                symbol.setStyle("-fx-background-radius: 0; -fx-padding: 0;");
            }
        });
    }

    private XYChart.Series<Number, Number> zeroLineSeries(int maxX) {
        XYChart.Series<Number, Number> zero = new XYChart.Series<>();
        zero.setName("neutral regulation");
        zero.getData().add(new XYChart.Data<>(0, 0));
        zero.getData().add(new XYChart.Data<>(maxX, 0));
        return zero;
    }

    private Label regulationAnnotation() {
        Label note = new Label(
            "Δμ < 0 : adaptive down-regulation\n" +
            "Δμ = 0 : neutral regulation\n" +
            "Δμ > 0 : increased regulation"
        );

        note.setStyle("""
            -fx-font-size: 11px;
            -fx-text-fill: #555;
            -fx-background-color: rgba(255,255,255,0.85);
            -fx-padding: 6 8 6 8;
            -fx-border-color: #ccc;
            -fx-border-radius: 4;
            -fx-background-radius: 4;
        """);

        return note;
    }

    /* ================= HEATMAP ================= */

    private void drawHeatmap() {
        if (heatmap == null || lastHeatmap == null || heatmapMu == null || heatmapNoise == null) return;
        if (lastHeatmap.length == 0 || lastHeatmap[0].length == 0) return;

        // Sanity check (keep, but also bail if truly inconsistent)
        if (lastHeatmap.length != heatmapMu.length || lastHeatmap[0].length != heatmapNoise.length) {
            System.out.println("⚠️ Heatmap dims mismatch: heat=" +
                lastHeatmap.length + "x" + lastHeatmap[0].length +
                " muLen=" + heatmapMu.length +
                " noiseLen=" + heatmapNoise.length);
            // You can choose to return here; I recommend it to avoid bad indexing.
            return;
        }

        final GraphicsContext g = heatmap.getGraphicsContext2D();

        final int nx = lastHeatmap.length;      // mu dimension
        final int ny = lastHeatmap[0].length;   // noise dimension

        final double w = heatmap.getWidth();
        final double h = heatmap.getHeight();
        if (w <= 0 || h <= 0) return;

        final double dx = w / nx;
        final double dy = h / ny;

        // Normalize colormap safely
        final double min = Arrays.stream(lastHeatmap).flatMapToDouble(Arrays::stream).min().orElse(0.0);
        final double max = Arrays.stream(lastHeatmap).flatMapToDouble(Arrays::stream).max().orElse(1.0);
        final double denom = (max - min);
        final double inv = (Math.abs(denom) < 1e-12) ? 0.0 : 1.0 / denom;

        // Clear + draw heatmap cells
        g.clearRect(0, 0, w, h);

        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                double v = (lastHeatmap[i][j] - min) * inv;
                v = clamp01(v);
                g.setFill(Color.color(v, 0, 1 - v));
                g.fillRect(i * dx, (ny - j - 1) * dy, dx, dy);
            }
        }

        // Nothing else to do if overlay is off / absent
        if (showCriticalMu == null || !showCriticalMu.isSelected()) return;
        if (lastMuCritical == null || heatmapNoise == null || heatmapNoise.length < 2) return;
        
        // Draw μc(η) curve
        final int m = Math.min(lastMuCritical.length, heatmapNoise.length);
        if (m >= 2) {
            g.save();
            g.setStroke(Color.WHITE);
            g.setLineWidth(3.0);
            g.setLineDashes(8, 6);

            for (int j = 0; j < m - 1; j++) {
                double mu1 = lastMuCritical[j];
                double mu2 = lastMuCritical[j + 1];
                if (!Double.isFinite(mu1) || !Double.isFinite(mu2)) continue;

                double x1 = muToX(mu1);
                double y1 = noiseToY(heatmapNoise[j]);

                double x2 = muToX(mu2);
                double y2 = noiseToY(heatmapNoise[j + 1]);

                g.strokeLine(x1, y1, x2, y2);
            }

            g.restore();
        }

        // Selected-noise μc* (nearest heatmapNoise row to current params.emotionalNoise)
        final int jStar = nearestIndex(heatmapNoise, params.emotionalNoise);
        final double muCStar = (jStar >= 0 && jStar < lastMuCritical.length)
                ? lastMuCritical[jStar]
                : Double.NaN;

        // Mean interior μ̄c (exclude boundary hits at muMin/muMax)
        final double muMin = heatmapMu[0];
        final double muMax = heatmapMu[heatmapMu.length - 1];
        final double muBar = meanFiniteInterior(lastMuCritical, muMin, muMax);
        double Y1=0;
        double X1=0;
        // Draw vertical marker for μc* (white) + label
        if (Double.isFinite(muCStar)) {
            drawVerticalMuMarkerOnCanvas(muCStar, Color.YELLOW);

            // label position near the point, clamped
            double xLabel = muToX(muCStar) + 15;
            double yLabel = noiseToY(heatmapNoise[jStar]) - 8;

            // Clamp label inside canvas
            xLabel = clamp(xLabel, 6, w - 120);
            yLabel = clamp(yLabel, 20, h );
            Y1=yLabel;
            X1=xLabel;
            String txt = String.format("μc = %.3f", muCStar);
            X1+=textWidth(txt, javafx.scene.text.Font.font(13));
            drawBoxedLabel(
                g,
                txt,
                xLabel,
                yLabel,
                Color.YELLOW,
                Color.color(0, 0, 0, 0.65),
                Color.color(1, 1, 1, 0.35)
            );
        }

        // Draw vertical marker + label for μ̄c — only if valid
        if (Double.isFinite(muBar)) {
            drawVerticalMuMarkerOnCanvas(muBar, Color.GREENYELLOW);

            double xText = muToX(muBar) + 6;
            xText = clamp(xText, 6, w - 120);
            String txt = String.format("μ̄c = %.3f", muBar);
            
            if(Y1>0) xText = xText +X1 + 35;
            double yText = 18;
            //if(Y1>0) yText = Y1;
            

            g.save();
            g.setFont(javafx.scene.text.Font.font(14));

            // Put it in a small dark box so it’s readable over red/blue
            drawBoxedLabel(
                g,
                txt,
                xText,
                yText,
                Color.GREENYELLOW,
                Color.color(0, 0, 0, 0.55),
                Color.color(1, 1, 1, 0.25)
            );
            g.restore();
        }
    }

    
    /**
     * Mean of finite values that are strictly inside (minInclusive, maxInclusive),
     * i.e., excludes boundary hits that indicate the true maximum is outside the scan range.
     */
    private static double meanFiniteInterior(double[] a, double minInclusive, double maxInclusive) {
        if (a == null || a.length == 0) return Double.NaN;
        double sum = 0.0;
        int n = 0;

        for (double v : a) {
            if (!Double.isFinite(v)) continue;
            if (v <= minInclusive || v >= maxInclusive) continue; // exclude boundary artifacts
            sum += v;
            n++;
        }
        return (n == 0) ? Double.NaN : (sum / n);
    }
    
    private void drawBoxedLabel(
        GraphicsContext gc,
        String text,
        double x,
        double y,
        Color textColor,
        Color bgColor,
        Color borderColor
    ) {
        javafx.scene.text.Font font = javafx.scene.text.Font.font(13);
        gc.setFont(font);

        double padding = 4;
        double tw = textWidth(text, font);
        double th = textHeight(text, font);

        double bx = x;
        double by = y - th;
        double bw = tw + 2 * padding;
        double bh = th + 2 * padding;

        // background
        gc.setFill(bgColor);
        gc.fillRoundRect(bx, by, bw, bh, 6, 6);

        // border
        gc.setStroke(borderColor);
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(bx, by, bw, bh, 6, 6);

        // text
        gc.setFill(textColor);
        gc.fillText(text, bx + padding, by + th + padding - 2);
    }
    
    
    private static int nearestIndex(double[] arr, double x) {
        if (arr == null || arr.length == 0) return -1;
        int best = 0;
        double bestD = Math.abs(arr[0] - x);
        for (int i = 1; i < arr.length; i++) {
            double d = Math.abs(arr[i] - x);
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }
    
    private void drawVerticalMuMarkerOnCanvas(double muValue, Color color) {
        if (heatmap == null || !Double.isFinite(muValue)) return;

        GraphicsContext gc = heatmap.getGraphicsContext2D();
        double w = heatmap.getWidth();
        double h = heatmap.getHeight();
        if (w <= 0 || h <= 0) return;

        double x = muToX(muValue);

        gc.save();
        gc.setStroke(color);
        gc.setLineWidth(2.5);
        gc.setLineDashes(10, 6);
        gc.strokeLine(x, 0, x, h);

        gc.setLineDashes(null);
        gc.setFill(color);

        gc.restore();
    }
    
    
    private void probeHeatmap(MouseEvent e) {
        if (lastHeatmap == null || heatmapMu == null || heatmapNoise == null) return;

        int i = (int)(e.getX() / (heatmap.getWidth() / lastHeatmap.length));
        int j = (int)((heatmap.getHeight() - e.getY()) / (heatmap.getHeight() / lastHeatmap[0].length));

        i = Math.max(0, Math.min(i, lastHeatmap.length - 1));
        j = Math.max(0, Math.min(j, lastHeatmap[0].length - 1));

        heatmapProbe.setText(String.format(
            "μ=%.3f  noise=%.4f  ⟨ΔC⟩=%.4f%s",
            heatmapMu[i],
            heatmapNoise[j],
            lastHeatmap[i][j],
            (lastMuCritical != null && j < lastMuCritical.length)
                ? String.format("   μc(η)=%.3f", lastMuCritical[j])
                : ""
        ));
    }

    /* PUB MODE */
    private void setPublicationLock(boolean lock) {
        // Guard in case called before UI construction in future refactors
        if (emotionalNoise != null) emotionalNoise.setDisable(lock);
        if (targetEntropy != null)  targetEntropy.setDisable(lock);
        if (muSlider != null)       muSlider.setDisable(lock);
    }

    /* ================= EXPORT ================= */

    private String snapshotFooter() {
        return String.format(
            "μ=%.3f   noise=%.3f   targetS=%.3f   mode=%s   %s",
            params.muInit,
            params.emotionalNoise,
            params.targetEntropy,
            sweepMode,
            java.time.LocalDateTime.now()
        );
    }

    private void exportPDF(Stage stage) {
        File f = save(stage, "PDF", "*.pdf");
        if (f == null) return;

        if (!f.getName().toLowerCase().endsWith(".pdf")) {
            f = new File(f.getParentFile(), f.getName() + ".pdf");
        }
        try (PDDocument doc = new PDDocument()) {

            PDPage page = new PDPage();
            doc.addPage(page);

            // Snapshot JavaFX scene
            WritableImage img = snapshotNodeFully(plotsRoot);

            File tmp = File.createTempFile("fximg", ".png");
            ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", tmp);

            PDImageXObject ximg = PDImageXObject.createFromFileByContent(tmp, doc);

            float pageW = page.getMediaBox().getWidth();
            float pageH = page.getMediaBox().getHeight();

            // Reserve a footer strip so text isn't covered by the screenshot
            float footerH = 42f;  // adjust as you like
            float margin  = 10f;

            // Fit image to page width and (pageH - footerH)
            float targetH = pageH - footerH;
            float imgW = (float) img.getWidth();
            float imgH = (float) img.getHeight();

            float scale = Math.min(pageW / imgW, targetH / imgH);
            float drawW = imgW * scale;
            float drawH = imgH * scale;

            float x0 = (pageW - drawW) / 2f;
            float y0 = footerH + (targetH - drawH) / 2f;

            // Draw the snapshot
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(ximg, x0, y0, drawW, drawH);
            }

            // Footer text
            String footer = snapshotFooter();

            // Use unicode font if possible; else ASCII-safe footer with Helvetica.
            PDFont font = getUnicodeFont(doc);
            String printableFooter = isUnicodeCapable(font) ? footer : asciiSafe(footer);

            float fontSize = 9f;

            // Compute width safely (after ASCII-safe transform if Helvetica)
            float textWidth = font.getStringWidth(printableFooter) / 1000f * fontSize;

            float tx = Math.max(margin, pageW - textWidth - margin);
            float ty = 18f; // baseline inside footer strip

            try (PDPageContentStream cs = new PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true
            )) {
                cs.beginText();
                cs.setFont(font, fontSize);
                cs.setNonStrokingColor(120);
                cs.newLineAtOffset(tx, ty);
                cs.showText(printableFooter);
                cs.endText();
            }

            doc.save(f);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    // ---- PDF font cache (avoid reloading font each export)
    private final Map<String, PDFont> pdfFontCache = new HashMap<>();

    private PDFont getUnicodeFont(PDDocument doc) {
        // Try common fonts packaged in JRE / project resources.
        // BEST: bundle a TTF in your resources: src/main/resources/fonts/DejaVuSans.ttf
        // Then load it from classpath.
        String key = "dejavu";
        if (pdfFontCache.containsKey(key)) return pdfFontCache.get(key);

        try (InputStream is = getClass().getResourceAsStream("/fonts/DejaVuSans.ttf")) {
            if (is != null) {
                PDFont f = PDType0Font.load(doc, is, true);
                pdfFontCache.put(key, f);
                return f;
            }
        } catch (Exception ignored) {}

        // Fallback attempt: macOS system font path (workslocally)
        try {
            Path p = Path.of("/System/Library/Fonts/Supplemental/Arial Unicode.ttf");
            if (Files.exists(p)) {
                try (InputStream is = Files.newInputStream(p)) {
                    PDFont f = PDType0Font.load(doc, is, true);
                    pdfFontCache.put(key, f);
                    return f;
                }
            }
        } catch (Exception ignored) {}

        // No unicode font found; caller should fallback to Helvetica + ASCII footer
        return PDType1Font.HELVETICA;
    }

    
    /**
     * NOTE: temporary fix for exporting plots.
     */
    private void exportPNG(Stage stage) {
        File f = save(stage, "PNG", "*.png");
        if (f == null) return;

        try {
            // Ensure CSS/layout are up-to-date
            plotsRoot.applyCss();
            plotsRoot.layout();

            // Save current sizes so we can restore afterwards
            double oldPrefW = plotsRoot.getPrefWidth();
            double oldPrefH = plotsRoot.getPrefHeight();

            // Force the root to its full computed size
            double fullW = Math.ceil(plotsRoot.prefWidth(-1));
            double fullH = Math.ceil(plotsRoot.prefHeight(fullW));

            plotsRoot.setPrefSize(fullW, fullH);
            plotsRoot.applyCss();
            plotsRoot.layout();

            SnapshotParameters sp = new SnapshotParameters();
            sp.setFill(Color.WHITE); // avoid transparent background

            WritableImage img = snapshotNodeFully(plotsRoot);
            WritableImage snap = plotsRoot.snapshot(sp, img);
            
            ImageIO.write(SwingFXUtils.fromFXImage(snap, null), "png", f);

            // Restore
            plotsRoot.setPrefWidth(oldPrefW);
            plotsRoot.setPrefHeight(oldPrefH);
            plotsRoot.applyCss();
            plotsRoot.layout();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    private void exportSVG(Stage stage) throws SVGGraphics2DIOException {
        File f = save(stage, "SVG", "*.svg");
        if (f == null) return;

        Document dom = GenericDOMImplementation.getDOMImplementation()
            .createDocument(null, "svg", null);
        SVGGraphics2D g = new SVGGraphics2D(dom);

        // TODO: render a snapshot image into SVG or use an appropriate exporter
        g.stream(f.getAbsolutePath());
    }
    
    private WritableImage snapshotNodeFully(Node node) {
        // Force CSS + layout so sizes are correct
        node.applyCss();
        node.layoutBoundsProperty();

        double w = node.getBoundsInParent().getWidth();
        double h = node.getBoundsInParent().getHeight();

        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.WHITE);

        WritableImage img = new WritableImage(
            (int) Math.ceil(w),
            (int) Math.ceil(h)
        );

        return node.snapshot(sp, img);
}
    private File save(Stage stage, String name, String glob) {
        FileChooser c = new FileChooser();
        c.setTitle("Export " + name);
        c.getExtensionFilters().add(new FileChooser.ExtensionFilter(name, glob));
        return c.showSaveDialog(stage);
    }

    /* ================= UTIL ================= */

    private double muToX(double mu) {
        return (mu - heatmapMu[0]) /
            (heatmapMu[heatmapMu.length - 1] - heatmapMu[0]) *
            heatmap.getWidth();
    }

    private double noiseToY(double noise) {
        return heatmap.getHeight() - (noise - heatmapNoise[0]) /
            (heatmapNoise[heatmapNoise.length - 1] - heatmapNoise[0]) *
            heatmap.getHeight();
    }

    
    
    public static void main(String[] args) {
        launch(args);
    }
}