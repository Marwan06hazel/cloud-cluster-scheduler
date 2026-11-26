package ui.cloudclusterschedulerui;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SimulatorView extends ScrollPane {

    @FunctionalInterface
    public interface SimulationListener {
        void onComplete(List<SimulationResult> results, int quantum, int cs);
    }

    private static final boolean CACHE_CARDS = true;

    private static final Color[] PALETTE = Stream.of(
                    "#2dd4bf", "#818cf8", "#f59e0b", "#fb7185", "#38bdf8", "#a3e635", "#e879f9", "#fb923c")
            .map(Color::web).toArray(Color[]::new);
    private static final Color MINT = PALETTE[0], INDIGO = PALETTE[1];
    private static final Color SLATE_800 = Color.web("#1e293b"), SLATE_700 = Color.web("#334155"),
            SLATE_500 = Color.web("#64748b"), SLATE_400 = Color.web("#94a3b8"),
            SLATE_300 = Color.web("#cbd5e1"), INK = Color.web("#061311");

    private static final Font BOLD_11 = Font.font("System", FontWeight.BOLD, 11);
    private static final Font BOLD_10_5 = Font.font("System", FontWeight.BOLD, 10.5);
    private static final Font NORMAL_10 = Font.font("System", FontWeight.NORMAL, 10);
    private static final Font NORMAL_11 = Font.font("System", 11);
    private static final Font NORMAL_12 = Font.font("System", FontWeight.NORMAL, 12);
    private static final Text METER = new Text();

    private static final double BAR_TOP = 2, BAR_H = 32;
    private static final String[] CHART_CATEGORIES = {"Avg waiting", "Avg turnaround"};
    private static final Insets LOG_PAD = new Insets(8, 10, 8, 10);

    // Line prefixes emitted by the schedulers (the two schedulers use different casing).
    private static final String SRTF_CS_LINE = "Context Switch:";
    private static final String ACS_CS_LINE = "Context switch";

    // ───────────────────────────── state ─────────────────────────────

    private final SimulationListener listener;
    private final ObservableList<ProcessRow> rows = FXCollections.observableArrayList();

    private final VBox processRowsBox = new VBox(8);
    private final VBox resultsBox = new VBox(18);
    private final TextField qField = new TextField("3");
    private final TextField csField = new TextField("1");
    private final ComboBox<String> algorithmCombo = new ComboBox<>(FXCollections.observableArrayList(
            "Compare Both", "Preemptive SJF (SRTF)", "Adaptive Container (ACS)"));
    private final Label procCount = new Label(), totalBurst = new Label(), algoCount = new Label();
    private final Label status = new Label("Ready to run");
    private final ChartCanvas arrivalWindow = new ChartCanvas(42);
    private Button tc1Btn, tc2Btn;

    public SimulatorView(SimulationListener listener) {
        this.listener = listener;

        getStyleClass().add("main-scroll");
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);

        setContent(styled(new VBox(20, topBar(), hero(), configGrid(), resultsSection(), footer()), "page-canvas"));

        arrivalWindow.setPainter(this::paintArrivals);
        rows.addListener((ListChangeListener<ProcessRow>) c -> {
            rebuildRows();
            refreshOverview();
        });
        loadTestCase2();
    }

    // ───────────────────────────── page sections ─────────────────────────────

    private Node topBar() {
        Circle dot = styled(new Circle(3.5), "mint-dot");
        status.getStyleClass().add("status-pill-text");
        HBox pill = styled(hbox(Pos.CENTER, 7, dot, status), "status-pill");
        VBox titles = new VBox(2,
                label("Workspace   /   Simulation", "breadcrumb-text"),
                label("CPU scheduler simulator", "top-bar-title"));
        return styled(hbox(Pos.CENTER_LEFT, 12, titles, grow(), pill), "top-header-bar");
    }

    private static Node hero() {
        VBox hero = new VBox(4,
                label("INTERACTIVE LAB   •   OPERATING SYSTEMS", "eyebrow-label"),
                label("Scheduling, visualized.", "hero-title"),
                label("Explore how SRTF and adaptive queues make decisions, one time unit at a time.", "hero-subtitle"));
        hero.setPadding(new Insets(4, 0, 2, 0));
        return hero;
    }

    private Node configGrid() {
        return columns(67.5,
                new VBox(16, processTableCard(), settingsCard()),
                new VBox(16, workloadCard(), schedulerCard()));
    }

    private Node footer() {
        HBox f = new HBox(10,
                label("✦   Deterministic simulation  ·  integer time units", "footer-note"), grow(),
                label("Local JavaFX studio  ·  no external backend", "footer-note"));
        f.setPadding(new Insets(8, 2, 0, 2));
        return f;
    }

    // ───────────────────────────── process table ─────────────────────────────

    private Node processTableCard() {
        tc1Btn = presetButton("Test case 1", this::loadTestCase1);
        tc2Btn = presetButton("Test case 2", this::loadTestCase2);
        Button loadFileBtn = presetButton("Load file", this::loadFile);

        GridPane heads = processGrid(new Insets(6, 4, 4, 4));
        Node[] names = labels("table-col-header", "PID", "ARRIVAL", "BURST", "PRIORITY");
        for (int i = 0; i < names.length; i++) heads.add(names[i], i, 0);

        Button add = styled(new Button("+  Add process"), "add-process-btn");
        add.setOnAction(e -> rows.add(new ProcessRow(nextPid(), "0", "3", "2")));

        return card(12,
                cardHeader("01 / WORKLOAD", "Process table", "card-title", hbox(Pos.CENTER_RIGHT, 8, tc1Btn, tc2Btn, loadFileBtn)),
                heads, processRowsBox, add);
    }

    private static Button presetButton(String text, Runnable action) {
        Button b = styled(new Button(text), "preset-btn");
        b.setOnAction(e -> action.run());
        return b;
    }

    private static GridPane processGrid(Insets pad) {
        GridPane gp = percentGrid(14, pad, 22, 24, 24, 24, 6);
        gp.setAlignment(Pos.CENTER_LEFT);
        gp.getColumnConstraints().get(4).setHalignment(HPos.RIGHT);
        return gp;
    }

    // One editable row. Built once (listeners attached once) and re-used when the table is rebuilt.
    private final class ProcessRow {
        final TextField pid, arrival, burst, priority;
        final Circle dot = new Circle(4);
        final GridPane node;

        ProcessRow(String p, String a, String b, String pr) {
            pid = input(p, "pid-inline-field");
            arrival = numeric(a);
            burst = numeric(b);
            priority = numeric(pr);
            arrival.textProperty().addListener((o, x, y) -> refreshOverview());
            burst.textProperty().addListener((o, x, y) -> refreshOverview());

            SVGPath trash = styled(new SVGPath(), "trash-icon");
            trash.setContent("M3 5h10M6 5V3h4v2m-5 2v7h6V7H5z");
            Button del = styled(new Button(), "icon-delete-btn");
            del.setGraphic(trash);
            del.setOnAction(e -> rows.remove(this));

            node = gridRow(processGrid(new Insets(5, 4, 5, 4)), "process-row-item",
                    hbox(Pos.CENTER_LEFT, 8, dot, pid), arrival, burst, priority, del);
        }
    }

    private static TextField numeric(String value) {
        TextField f = input(value, "dark-input");
        f.setMaxWidth(Double.MAX_VALUE);
        return f;
    }

    private void rebuildRows() {
        for (int i = 0; i < rows.size(); i++) rows.get(i).dot.setFill(PALETTE[i % PALETTE.length]);
        processRowsBox.getChildren().setAll(rows.stream().map(r -> r.node).collect(Collectors.toList()));
    }

    private String nextPid() {
        int n = rows.size();
        String id;
        do {
            id = "P" + (++n);
        } while (pidTaken(id));
        return id;
    }

    private boolean pidTaken(String id) {
        return rows.stream().anyMatch(r -> r.pid.getText().trim().equals(id));
    }

    // ───────────────────────────── settings ─────────────────────────────

    private Node settingsCard() {
        Button reset = styled(new Button("⇅"), "square-icon-btn");
        reset.setTooltip(new Tooltip("Reset Q=3, CS=1"));
        reset.setOnAction(e -> {
            qField.setText("3");
            csField.setText("1");
            algorithmCombo.getSelectionModel().select(0);
        });

        TextField aging = new TextField("4");
        aging.setEditable(false);
        aging.setTooltip(new Tooltip("Fixed aging interval (4 units) in Aging.java"));

        algorithmCombo.getSelectionModel().select(0);
        algorithmCombo.getStyleClass().add("dark-combo");
        algorithmCombo.setMaxWidth(Double.MAX_VALUE);
        algorithmCombo.valueProperty().addListener((o, a, b) -> refreshOverview());

        GridPane params = percentGrid(12, Insets.EMPTY, 25, 25, 25, 25);
        params.add(unitInput("Time quantum", qField, "Q"), 0, 0);
        params.add(unitInput("Context switch", csField, "CS"), 1, 0);
        params.add(unitInput("Aging interval", aging, ""), 2, 0);
        params.add(new VBox(6, label("Algorithm", "field-label"), algorithmCombo), 3, 0);

        Button run = styled(new Button("▶   Run simulation"), "mint-cta-button");
        run.setMaxWidth(Double.MAX_VALUE);
        run.setDefaultButton(true);
        run.setOnAction(e -> runSimulation());

        return card(14, cardHeader("02 / CONFIGURATION", "Simulation settings", "card-title", reset), params, run);
    }

    private static VBox unitInput(String caption, TextField field, String unit) {
        field.getStyleClass().setAll("unit-inner-field");
        HBox.setHgrow(field, Priority.ALWAYS);
        HBox wrap = styled(hbox(Pos.CENTER_LEFT, 6, field), "unit-input-wrapper");
        if (!unit.isEmpty()) wrap.getChildren().add(label(unit, "unit-suffix"));
        return new VBox(6, label(caption, "field-label"), wrap);
    }

    // ───────────────────────────── workload profile + scheduler info ─────────────────────────────

    private Node workloadCard() {
        SVGPath chip = styled(new SVGPath(), "brand-logo-icon");
        chip.setContent("M5 2h6v2h2v2h2v6h-2v2h-2v2H5v-2H3v-2H1V6h2V4h2V2zm1 4v6h4V6H6z");
        StackPane badge = styled(new StackPane(chip), "overview-chip-badge");

        HBox tiles = new HBox(10,
                statTile("PROCESSES", procCount, ""),
                statTile("TOTAL BURST", totalBurst, "u"),
                statTile("ALGORITHMS", algoCount, ""));

        VBox window = new VBox(6,
                label("ARRIVAL WINDOW", "table-col-header"),
                arrivalWindow,
                label("Process arrivals over simulation time", "micro-caption"));
        window.setPadding(new Insets(4, 0, 0, 0));

        return card(14, cardHeader("SYSTEM OVERVIEW", "Workload profile", "card-title", badge), tiles, window);
    }

    private static VBox statTile(String caption, Label value, String unit) {
        value.getStyleClass().add("stat-box-value");
        HBox val = hbox(Pos.BASELINE_LEFT, 3, value);
        if (!unit.isEmpty()) val.getChildren().add(label(unit, "stat-box-unit"));
        VBox box = styled(new VBox(4, label(caption, "stat-box-caption"), val), "inset-stat-box");
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private static Node schedulerCard() {
        return card(12,
                label("ACTIVE SCHEDULER", "card-eyebrow"),
                schedulerRow("S", "badge-purple", "Preemptive SJF", "Shortest remaining time first", "SRTF"),
                schedulerRow("A", "badge-mint", "Adaptive container", "RR → priority → aging", "ACS"));
    }

    private static Node schedulerRow(String letter, String badgeCss, String name, String desc, String tag) {
        StackPane icon = styled(new StackPane(label(letter, "scheduler-letter")), "scheduler-icon-box", badgeCss);
        VBox info = new VBox(2, label(name, "scheduler-row-title"), label(desc, "scheduler-row-sub"));
        return styled(hbox(Pos.CENTER_LEFT, 11, icon, info, grow(), label(tag, "pill-tag")), "scheduler-badge-row");
    }

    private void refreshOverview() {
        int burst = 0;
        for (ProcessRow r : rows) burst += Math.max(0, parse(r.burst, 0));
        procCount.setText(String.format("%02d", rows.size()));
        totalBurst.setText(String.valueOf(burst));
        algoCount.setText(algorithmCombo.getSelectionModel().getSelectedIndex() == 0 ? "02" : "01");
        arrivalWindow.redraw();
    }

    private void paintArrivals(GraphicsContext g, double w, double h) {
        g.setFill(SLATE_800);
        for (int i = 0; i <= 6; i++) g.fillRect(w / 6 * i, 4, 1, h - 8);

        int[] arrivals = new int[rows.size()];
        int max = 6;
        for (int i = 0; i < arrivals.length; i++) {
            arrivals[i] = Math.max(0, parse(rows.get(i).arrival, 0));
            max = Math.max(max, arrivals[i] + 2);
        }
        for (int i = 0; i < arrivals.length; i++) {
            double x = Math.min(w - 8, arrivals[i] / (double) max * (w - 16) + 2);
            g.setFill(PALETTE[i % PALETTE.length]);
            g.fillRoundRect(x, 6, 5, h - 12, 3, 3);
        }
    }

    // ───────────────────────────── results shell ─────────────────────────────

    private Node resultsSection() {
        Button reset = styled(new Button("↻   Reset output"), "reset-output-btn");
        reset.setOnAction(e -> {
            showPlaceholder();
            setVvalue(0);
        });
        showPlaceholder();

        VBox box = new VBox(14, cardHeader("03 / RESULTS", "Simulation output", "section-main-title", reset), resultsBox);
        box.setPadding(new Insets(6, 0, 0, 0));
        return box;
    }

    private void showPlaceholder() {
        status.setText("Ready to run");
        Label msg = label("Configure processes above and click \"Run simulation\" to render Gantt timelines, execution logs, and comparison charts.", "empty-state-text");
        msg.setWrapText(true);
        resultsBox.getChildren().setAll(styled(new StackPane(msg), "dark-card", "empty-state-card"));
    }

    // ───────────────────────────── presets & file loading ─────────────────────────────

    /** {@code flat} holds consecutive groups of four values: pid, arrival, burst, priority. */
    private void setPreset(int q, int cs, Button active, String... flat) {
        qField.setText(String.valueOf(q));
        csField.setText(String.valueOf(cs));
        tc1Btn.getStyleClass().remove("preset-btn-active");
        tc2Btn.getStyleClass().remove("preset-btn-active");
        if (active != null) active.getStyleClass().add("preset-btn-active");

        List<ProcessRow> next = new ArrayList<>();
        for (int i = 0; i + 3 < flat.length; i += 4) {
            next.add(new ProcessRow(flat[i], flat[i + 1], flat[i + 2], flat[i + 3]));
        }
        rows.setAll(next);
    }

    private void loadTestCase1() {
        setPreset(3, 0, tc1Btn, "P1", "0", "4", "2", "P2", "1", "7", "3", "P3", "3", "2", "1");
    }

    private void loadTestCase2() {
        setPreset(3, 1, tc2Btn, "P1", "0", "5", "4", "P2", "1", "6", "2", "P3", "4", "4", "3");
    }

    private void loadFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load Workload File");
        chooser.setInitialDirectory(new File("."));
        File file = chooser.showOpenDialog(owner());
        if (file == null) return;

        try (Scanner sc = new Scanner(file)) {
            Main.Input in = Main.read(sc);
            List<String> flat = new ArrayList<>();
            for (Process p : in.processes) {
                Collections.addAll(flat, p.id, String.valueOf(p.arrival), String.valueOf(p.burst), String.valueOf(p.priority));
            }
            setPreset(in.q, in.cs, null, flat.toArray(new String[0]));
        } catch (Exception ex) {
            showError("Could not read file: " + ex.getMessage());
        }
    }

    // ───────────────────────────── run simulation ─────────────────────────────

    private static final class Config {
        int q, cs;
        final List<Process> processes = new ArrayList<>();
        final Map<String, Color> colors = new LinkedHashMap<>();
    }

    private Config readConfig() {
        Config c = new Config();
        c.q = Integer.parseInt(qField.getText().trim());
        c.cs = Integer.parseInt(csField.getText().trim());
        if (c.q < 1) throw new IllegalArgumentException("Time quantum (Q) must be at least 1.");
        if (c.cs < 0) throw new IllegalArgumentException("Context switch (CS) cannot be negative.");

        for (int i = 0; i < rows.size(); i++) {
            ProcessRow r = rows.get(i);
            String id = r.pid.getText().trim();
            int arrival = Integer.parseInt(r.arrival.getText().trim());
            int burst = Integer.parseInt(r.burst.getText().trim());
            int priority = Integer.parseInt(r.priority.getText().trim());

            if (id.isEmpty()) throw new IllegalArgumentException("Row " + (i + 1) + " is missing a PID.");
            if (c.colors.containsKey(id)) throw new IllegalArgumentException("Duplicate process ID: " + id);
            if (burst <= 0) throw new IllegalArgumentException(id + ": burst time must be greater than 0.");
            if (arrival < 0) throw new IllegalArgumentException(id + ": arrival time cannot be negative.");

            c.processes.add(new Process(id, arrival, burst, priority));
            c.colors.put(id, PALETTE[i % PALETTE.length]);
        }
        if (c.processes.isEmpty()) throw new IllegalArgumentException("Add at least one process first.");
        return c;
    }

    public void runSimulation() {
        Config cfg;
        try {
            cfg = readConfig();
        } catch (NumberFormatException ex) {
            showError("Please enter valid whole numbers for Q, CS, Arrival, Burst, and Priority.");
            return;
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
            return;
        }

        List<SimulationResult> all = new ArrayList<>();
        SimulationResult sjf = null, acs = null;
        for (Scheduler s : Main.schedulers()) {
            SimulationResult res = s.run(cfg.processes, cfg.q, cfg.cs);
            all.add(res);
            if (s instanceof SJFScheduler || s.name().toLowerCase(Locale.ROOT).contains("sjf")) sjf = res;
            else acs = res;
        }

        int mode = algorithmCombo.getSelectionModel().getSelectedIndex();
        if (mode == 0 && sjf != null && acs != null) {
            resultsBox.getChildren().setAll(
                    columns(50, resultCard(true, sjf, cfg.colors), resultCard(false, acs, cfg.colors)),
                    columns(50, logCard(true, sjf), logCard(false, acs)),
                    chartCard(acs, sjf));
        } else {
            SimulationResult chosen = (mode == 1 && sjf != null) ? sjf : acs;
            if (chosen != null) {
                boolean isSjf = chosen == sjf;
                resultsBox.getChildren().setAll(resultCard(isSjf, chosen, cfg.colors), logCard(isSjf, chosen));
            } else {
                resultsBox.getChildren().clear();
            }
        }

        status.setText("Simulation complete");
        if (listener != null && !all.isEmpty()) listener.onComplete(all, cfg.q, cfg.cs);
    }

    // ───────────────────────────── algorithm card: Gantt + metrics ─────────────────────────────

    private static Node resultCard(boolean sjf, SimulationResult r, Map<String, Color> colors) {
        ChartCanvas gantt = new ChartCanvas(58);
        gantt.setPainter((g, w, h) -> paintGantt(g, w, r, colors));
        gantt.setHover((x, y, w, h) -> ganttTip(r, x, y, w));

        return card(16,
                cardHeader(sjf ? "PREEMPTIVE SJF" : "ADAPTIVE CONTAINER",
                        sjf ? "Shortest remaining time" : "Tiered round robin", "card-title",
                        label(sjf ? "SRTF" : "ACS", "pill-tag")),
                gantt, metricsTable(r, colors));
    }

    private static void paintGantt(GraphicsContext g, double w, SimulationResult r, Map<String, Color> colors) {
        if (r.gantt.isEmpty()) return;
        int total = r.gantt.get(r.gantt.size() - 1).end;
        if (total <= 0) return;

        double scale = w / total, tickY = BAR_TOP + BAR_H + 16;
        for (int i = 0; i < r.gantt.size(); i++) {
            SimulationResult.GanttSlot s = r.gantt.get(i);
            double x = s.start * scale, sw = (s.end - s.start) * scale;
            boolean cs = s.pid.equals("CS"), idle = s.pid.equals("IDLE");

            g.setFill(cs ? SLATE_700 : idle ? SLATE_800 : colors.getOrDefault(s.pid, MINT));
            g.fillRoundRect(x + 0.75, BAR_TOP, Math.max(sw - 1.5, 2), BAR_H, 6, 6);

            if (textWidth(s.pid, BOLD_11) <= sw - 4) {
                text(g, s.pid, x + sw / 2, BAR_TOP + BAR_H / 2, TextAlignment.CENTER, VPos.CENTER, BOLD_11,
                        cs ? SLATE_300 : idle ? SLATE_400 : INK);
            }
            text(g, String.valueOf(s.start), x, tickY, i == 0 ? TextAlignment.LEFT : TextAlignment.CENTER,
                    VPos.BASELINE, NORMAL_10, SLATE_500);
        }
        text(g, String.valueOf(total), w, tickY, TextAlignment.RIGHT, VPos.BASELINE, NORMAL_10, SLATE_500);
    }

    private static String ganttTip(SimulationResult r, double x, double y, double w) {
        if (r.gantt.isEmpty() || y < BAR_TOP || y > BAR_TOP + BAR_H) return null;
        int total = r.gantt.get(r.gantt.size() - 1).end;
        if (total <= 0) return null;
        for (SimulationResult.GanttSlot s : r.gantt) {
            if (x >= s.start * w / total && x < s.end * w / total) {
                return s.pid + "  [" + s.start + " → " + s.end + "]  (" + (s.end - s.start) + "u)";
            }
        }
        return null;
    }

    private static Node metricsTable(SimulationResult r, Map<String, Color> colors) {
        VBox table = new VBox();
        table.getChildren().add(metricsRow("metrics-header-row",
                labels("metrics-col-header", "Process", "Arrival", "Finish", "Turnaround", "Waiting", "Response")));

        List<Process> sorted = new ArrayList<>(r.processes);
        sorted.sort(Comparator.comparingInt((Process p) -> p.id.length()).thenComparing(p -> p.id));
        for (Process p : sorted) {
            table.getChildren().add(metricsRow("metrics-data-row",
                    hbox(Pos.CENTER_LEFT, 7, new Circle(3.5, colors.getOrDefault(p.id, MINT)), label(p.id, "metrics-pid-cell")),
                    label(String.valueOf(p.arrival), "metrics-cell"),
                    label(String.valueOf(p.finish), "metrics-cell"),
                    label(String.valueOf(p.turnaround()), "metrics-cell"),
                    label(String.valueOf(p.waiting()), "metrics-cell"),
                    label(String.valueOf(p.response()), "metrics-cell")));
        }

        table.getChildren().add(metricsRow("metrics-avg-row",
                label("Average", "metrics-avg-label"),
                label("—", "metrics-muted-cell"),
                label("—", "metrics-muted-cell"),
                label(fmt(Metrics.avgTurnaround(r)), "metrics-avg-value"),
                label(fmt(Metrics.avgWaiting(r)), "metrics-avg-value"),
                label(fmt(Metrics.avgResponse(r)), "metrics-avg-value")));
        return table;
    }

    private static GridPane metricsRow(String css, Node... cells) {
        return gridRow(percentGrid(0, new Insets(9, 6, 9, 6), 18, 15, 15, 18, 17, 17), css, cells);
    }

    // ───────────────────────────── execution logs ─────────────────────────────

    private static Node logCard(boolean sjf, SimulationResult r) {
        return sjf ? srtfLogCard(r) : acsLogCard(r);
    }

    private static Node srtfLogCard(SimulationResult r) {
        VBox rowsBox = new VBox();
        for (SimulationResult.GanttSlot s : r.gantt) {
            String running = s.pid.equals("CS") ? r.log.getOrDefault(s.start, "Context switch").split("\n")[0]
                    : s.pid.equals("IDLE") ? "CPU Idle"
                      : s.pid;

            List<String> details = new ArrayList<>();
            for (int t = s.start; t < s.end; t++) {
                String entry = r.log.get(t);
                if (entry == null || entry.isBlank()) continue;
                for (String line : entry.split("\n")) {
                    if (!line.startsWith(SRTF_CS_LINE)) details.add("t=" + t + ": " + line);
                }
            }

            rowsBox.getChildren().add(gridRow(logGrid(13, 39, 48), "metrics-data-row",
                    wrapLabel(span(s), "log-time-cell"),
                    wrapLabel(running, "log-text-cell"),
                    wrapLabel(details.isEmpty() ? "[Empty]" : String.join("\n", details), "log-text-cell")));
        }

        Node header = gridRow(logGrid(13, 39, 48), "metrics-header-row",
                labels("metrics-col-header", "Time", "Running process", "Ready queue / Events"));
        VBox log = styled(new VBox(header, innerScroll(rowsBox, 260, false)), "log-box");
        return card(12, titleBlock("SRTF", "Per-time-step execution log", "card-title"), log);
    }

    private static Node acsLogCard(SimulationResult r) {
        double[] cols = {58, 185, 130, 270};
        String tier1 = "Tier 1: [Empty]", tier2 = "Tier 2: [Empty]";

        List<String> aging = new ArrayList<>();
        for (Map.Entry<Integer, String> e : r.log.entrySet()) {
            for (String line : e.getValue().split("\n")) {
                if (line.contains(": Waiting =")) aging.add("T=" + e.getKey() + "  |  " + line);
            }
        }

        VBox rowsBox = new VBox();
        for (SimulationResult.GanttSlot s : r.gantt) {
            String running = s.pid;
            for (String line : r.log.getOrDefault(s.start, "").split("\n")) {
                if (line.startsWith(ACS_CS_LINE)) running = line;
                else if (line.startsWith("Tier 1:")) tier1 = line;
                else if (line.startsWith("Tier 2:")) tier2 = line;
            }
            rowsBox.getChildren().add(gridRow(fixedGrid(cols), "metrics-data-row",
                    wrapLabel(span(s), "log-time-cell"),
                    wrapLabel(running, "log-text-cell"),
                    wrapLabel(tier1, "log-text-cell"),
                    wrapLabel(tier2, "log-text-cell")));
        }

        Node header = gridRow(fixedGrid(cols), "metrics-header-row",
                labels("metrics-col-header", "Time", "Running process", "Tier 1 queue", "Tier 2 queue"));
        VBox log = styled(new VBox(innerScroll(new VBox(header, rowsBox), 172, true)), "log-box");

        VBox feed = new VBox(4);
        if (aging.isEmpty()) {
            feed.getChildren().add(label("No Tier-2 aging events occurred during this run.", "aging-line-muted"));
        } else {
            for (String line : aging) feed.getChildren().add(wrapLabel(line, "aging-line"));
        }
        VBox agingBox = styled(new VBox(6,
                label("AGING PRIORITY CALCULATIONS", "table-col-header"),
                innerScroll(feed, 60, false)), "aging-panel");

        return card(12, titleBlock("ACS", "Queue snapshots & aging", "card-title"), log, agingBox);
    }

    private static String span(SimulationResult.GanttSlot s) {
        return s.end - s.start == 1 ? String.valueOf(s.start) : s.start + "–" + s.end;
    }

    private static GridPane logGrid(double... percents) {
        return percentGrid(10, LOG_PAD, percents);
    }

    private static GridPane fixedGrid(double... widths) {
        GridPane gp = new GridPane();
        gp.setHgap(10);
        gp.setPadding(LOG_PAD);
        for (double w : widths) {
            ColumnConstraints cc = new ColumnConstraints(w);
            cc.setMinWidth(w);
            gp.getColumnConstraints().add(cc);
        }
        return gp;
    }

    private static ScrollPane innerScroll(Node content, double height, boolean horizontal) {
        ScrollPane sp = styled(new ScrollPane(content), "inner-log-scroll");
        sp.setFitToWidth(!horizontal);
        sp.setHbarPolicy(horizontal ? ScrollBarPolicy.AS_NEEDED : ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        sp.setMinHeight(height);
        sp.setPrefHeight(height);
        sp.setMaxHeight(height);
        return sp;
    }

    // ───────────────────────────── at-a-glance bar chart ─────────────────────────────

    private static Node chartCard(SimulationResult acs, SimulationResult sjf) {
        double[][] vals = {
                {Metrics.avgWaiting(sjf), Metrics.avgWaiting(acs)},
                {Metrics.avgTurnaround(sjf), Metrics.avgTurnaround(acs)}
        };
        double rawMax = Math.max(4.0, Math.max(Math.max(vals[0][0], vals[0][1]), Math.max(vals[1][0], vals[1][1])));
        double max = Math.ceil(rawMax / 4.0) * 4.0;

        Map<Rectangle2D, String> bars = new LinkedHashMap<>();
        ChartCanvas chart = new ChartCanvas(195);
        chart.setPainter((g, w, h) -> paintBars(g, w, h, max, vals, bars));
        chart.setHover((x, y, w, h) -> bars.entrySet().stream()
                .filter(e -> e.getKey().contains(x, y)).map(Map.Entry::getValue).findFirst().orElse(null));

        HBox legend = hbox(Pos.CENTER, 18, legendChip("ACS", MINT), legendChip("SJF", INDIGO));
        return card(12, titleBlock("AT A GLANCE", "Average time by algorithm", "card-title"), chart, legend);
    }

    private static void paintBars(GraphicsContext g, double w, double h, double max, double[][] vals,
                                  Map<Rectangle2D, String> bars) {
        bars.clear();
        if (w <= 80 || h <= 60) return;

        double left = 46, top = 20, plotW = w - left - 24, plotH = h - top - 32, base = top + plotH;

        for (int i = 0; i <= 4; i++) {
            double y = Math.round(base - i / 4.0 * plotH);
            g.setFill(i == 0 ? SLATE_700 : SLATE_800);
            if (i == 0) {
                g.fillRect(left, y, plotW, 1);
            } else {
                for (double x = left; x < left + plotW; x += 8) g.fillRect(x, y, Math.min(4, left + plotW - x), 1);
            }
            text(g, String.format(Locale.US, "%.0f", max / 4 * i), left - 10, y + 4,
                    TextAlignment.RIGHT, VPos.BASELINE, NORMAL_11, SLATE_500);
        }
        g.setFill(SLATE_700);
        g.fillRect(left, top, 1, plotH);

        double group = plotW / 2, barW = Math.min(155, group * 0.36), gap = 8;
        for (int c = 0; c < vals.length; c++) {
            double cx = left + group * (c + 0.5);
            for (int k = 0; k < 2; k++) { // k == 0 → SJF, k == 1 → ACS
                double v = vals[c][k];
                double bh = Math.max(3, v / max * plotH);
                double x = k == 0 ? cx - barW - gap / 2 : cx + gap / 2;
                double y = base - bh;

                g.setFill(k == 0 ? INDIGO : MINT);
                g.fillRoundRect(x, y, barW, bh, 6, 6);
                text(g, fmt(v), x + barW / 2, Math.max(top - 4, y - 5),
                        TextAlignment.CENTER, VPos.BASELINE, BOLD_10_5, SLATE_300);
                bars.put(new Rectangle2D(x, y, barW, bh), (k == 0 ? "SJF" : "ACS") + ": " + fmt(v) + " time units");
            }
            text(g, CHART_CATEGORIES[c], cx, base + 21, TextAlignment.CENTER, VPos.BASELINE, NORMAL_12, SLATE_400);
        }
    }

    private static Node legendChip(String name, Color color) {
        Rectangle swatch = new Rectangle(10, 10, color);
        swatch.setArcWidth(2);
        swatch.setArcHeight(2);
        return hbox(Pos.CENTER, 6, swatch, label(name, "chart-legend-label"));
    }

    // ───────────────────────────── canvas drawing helpers ─────────────────────────────

    private static void text(GraphicsContext g, String s, double x, double y,
                             TextAlignment align, VPos baseline, Font font, Color color) {
        g.setFont(font);
        g.setFill(color);
        g.setTextAlign(align);
        g.setTextBaseline(baseline);
        g.fillText(s, x, y);
    }

    private static double textWidth(String s, Font font) {
        METER.setFont(font);
        METER.setText(s);
        return METER.getLayoutBounds().getWidth();
    }


    // A single Canvas that repaints only when its size changes or redraw() is called.
    private static final class ChartCanvas extends Region {
        interface Painter { void paint(GraphicsContext g, double w, double h); }

        interface Hover { String text(double x, double y, double w, double h); }

        private final Canvas canvas = new Canvas();
        private final Tooltip tip = new Tooltip();
        private Painter painter;
        private boolean dirty = true;

        ChartCanvas(double height) {
            setMinHeight(height);
            setPrefHeight(height);
            setMaxHeight(height);
            canvas.setManaged(false);
            getChildren().add(canvas);

            addEventFilter(ScrollEvent.SCROLL, e -> tip.hide());
            setOnMouseExited(e -> tip.hide());
            sceneProperty().addListener((o, a, scene) -> {
                if (scene == null) tip.hide();
            });
        }

        void setPainter(Painter p) {
            painter = p;
            redraw();
        }

        void setHover(Hover hover) {
            setOnMouseMoved(e -> {
                String t = hover.text(e.getX(), e.getY(), getWidth(), getHeight());
                if (t == null) {
                    tip.hide();
                    return;
                }
                tip.setText(t);
                double ax = e.getScreenX() + 12, ay = e.getScreenY() + 14;
                if (tip.isShowing()) {
                    tip.setAnchorX(ax);
                    tip.setAnchorY(ay);
                } else {
                    tip.show(this, ax, ay);
                }
            });
        }

        void redraw() {
            dirty = true;
            requestLayout();
        }

        @Override
        protected void layoutChildren() {
            double w = getWidth(), h = getHeight();
            if (painter == null || w <= 10 || h <= 10) return;
            if (canvas.getWidth() != w || canvas.getHeight() != h) {
                canvas.setWidth(w);
                canvas.setHeight(h);
                dirty = true;
            }
            if (!dirty) return;
            dirty = false;
            GraphicsContext g = canvas.getGraphicsContext2D();
            g.clearRect(0, 0, w, h);
            painter.paint(g, w, h);
        }
    }

    // ───────────────────────────── tiny UI helpers ─────────────────────────────

    private static <T extends Node> T styled(T node, String... classes) {
        node.getStyleClass().addAll(classes);
        return node;
    }

    private static Label label(String text, String css) {
        return styled(new Label(text), css);
    }

    private static Label wrapLabel(String text, String css) {
        Label l = label(text, css);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    private static Node[] labels(String css, String... texts) {
        Node[] out = new Node[texts.length];
        for (int i = 0; i < texts.length; i++) out[i] = label(texts[i], css);
        return out;
    }

    private static TextField input(String text, String css) {
        TextField f = new TextField(text);
        f.getStyleClass().setAll(css);
        return f;
    }

    private static Region grow() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private static HBox hbox(Pos align, double gap, Node... kids) {
        HBox b = new HBox(gap, kids);
        b.setAlignment(align);
        return b;
    }

    private static VBox titleBlock(String eyebrow, String title, String titleCss) {
        return new VBox(2, label(eyebrow, "card-eyebrow"), label(title, titleCss));
    }

    private static HBox cardHeader(String eyebrow, String title, String titleCss, Node trailing) {
        return hbox(Pos.CENTER_LEFT, 10, titleBlock(eyebrow, title, titleCss), grow(), trailing);
    }

    private static VBox card(double gap, Node... kids) {
        VBox c = styled(new VBox(gap, kids), "dark-card");
        if (CACHE_CARDS) {
            c.setCache(true);
            c.setCacheHint(CacheHint.SPEED);
        }
        return c;
    }

    private static GridPane percentGrid(double hgap, Insets pad, double... percents) {
        GridPane gp = new GridPane();
        gp.setHgap(hgap);
        gp.setPadding(pad);
        for (double p : percents) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(p);
            cc.setHgrow(Priority.ALWAYS);
            gp.getColumnConstraints().add(cc);
        }
        return gp;
    }

    private static GridPane columns(double leftPercent, Node left, Node right) {
        GridPane gp = percentGrid(18, Insets.EMPTY, leftPercent, 100 - leftPercent);
        gp.setVgap(18);
        gp.add(left, 0, 0);
        gp.add(right, 1, 0);
        return gp;
    }

    private static GridPane gridRow(GridPane grid, String css, Node... cells) {
        if (css != null) grid.getStyleClass().add(css);
        for (int i = 0; i < cells.length; i++) grid.add(cells[i], i, 0);
        return grid;
    }

    private static int parse(TextField f, int fallback) {
        try {
            return Integer.parseInt(f.getText().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.2f", v);
    }

    private Window owner() {
        return getScene() == null ? null : getScene().getWindow();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setTitle("Simulation Input Error");
        alert.setHeaderText(null);
        Window owner = owner();
        if (owner != null) alert.initOwner(owner);
        alert.showAndWait();
    }
}