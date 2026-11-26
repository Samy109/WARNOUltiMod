package com.warnomodmaker.gui.maps;

import com.warnomodmaker.combine.Cancel;
import com.warnomodmaker.combine.CombineException;
import com.warnomodmaker.combine.MapModeConverter;
import com.warnomodmaker.combine.MapModeConverter.Mode;
import com.warnomodmaker.combine.MapModeConverter.Scenario;
import com.warnomodmaker.combine.MapModeConverter.Settings;
import com.warnomodmaker.combine.MapModeConverter.Zone;
import com.warnomodmaker.combine.Models.WarnoPaths;
import com.warnomodmaker.combine.ProcessRunner;
import com.warnomodmaker.combine.WarnoLocator;
import com.warnomodmaker.gui.theme.RoundedPanel;
import com.warnomodmaker.gui.theme.WarnoTheme;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * The Maps workspace: rebuilds WARNO skirmish scenarios as new scenarios in a mod, with the game mode (Conquest or
 * Destruction) and other map parameters changed: lobby name, players, environments, size and density labels, rivers
 * and each command zone's points.
 */
public final class MapModePanel extends JPanel {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final char ICON_FOLDER = '', ICON_REFRESH = '', ICON_MAP = '', ICON_CONVERT = '';

    private final MapModeConverter converter = new MapModeConverter(new ProcessRunner());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "map-mode-operation");
        thread.setDaemon(true);
        return thread;
    });

    private WarnoPaths paths;
    private volatile Cancel operation;
    private volatile boolean busy;
    private boolean started;
    private final Map<Scenario, Settings> settings = new IdentityHashMap<>();
    private Scenario editing;
    private boolean loadingEditor;

    // Sidebar
    private final JTextField warnoPathBox = new JTextField();
    private final JButton browseButton = WarnoTheme.iconButton(ICON_FOLDER, "Choose the WARNO folder");
    private final JButton refreshButton = WarnoTheme.iconButton(ICON_REFRESH, "Rescan WARNO's scenarios");
    private final JTextField modNameBox = new JTextField("UltiMaps");
    private final JLabel selectionText = WarnoTheme.caption("");
    private final JButton convertButton = WarnoTheme.accentButton("Build into mod", ICON_CONVERT);
    private final JButton cancelButton = new JButton("Cancel");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel progressText = WarnoTheme.caption("");

    // Scenario list
    private final JTextField searchBox = new JTextField();
    private final JComboBox<String> modeFilter = new JComboBox<>(new String[]{"All modes", "Conquest", "Destruction"});
    private final ScenarioModel model = new ScenarioModel();
    private final JTable table = new JTable(model);
    private final TableRowSorter<ScenarioModel> sorter = new TableRowSorter<>(model);
    private final JTextArea logBox = new JTextArea();

    // Settings editor
    private final JLabel editorTitle = WarnoTheme.title("Choose a scenario", 15);
    private final JLabel editorSource = WarnoTheme.caption("Its settings appear here.");
    private final JComboBox<Mode> modeBox = new JComboBox<>(Mode.values());
    private final JTextField scenarioNameBox = new JTextField();
    private final JTextField displayNameBox = new JTextField();
    private final JSpinner slotsBox = new JSpinner(new SpinnerNumberModel(4, 2, 20, 1));
    private final JComboBox<String> sizeBox = new JComboBox<>(MapModeConverter.MAP_SIZES.toArray(new String[0]));
    private final JComboBox<String> densityBox = new JComboBox<>(MapModeConverter.DENSITIES.toArray(new String[0]));
    private final JCheckBox riversBox = new JCheckBox("Has rivers (shown in the detailed map list)");
    private final JPanel environmentsPanel = new JPanel();
    private final ZoneModel zoneModel = new ZoneModel();
    private final JTable zoneTable = new JTable(zoneModel);
    private final JSpinner scaleBox = new JSpinner(new SpinnerNumberModel(100, 0, 1000, 10));
    private final JButton scaleButton = new JButton("Scale all");
    private final JButton resetButton = new JButton("Reset to the source values");
    private final JPanel editorBody = new JPanel();

    public MapModePanel() {
        super(new BorderLayout());
        setBackground(WarnoTheme.BACKGROUND);
        add(buildSidebar(), BorderLayout.WEST);
        add(buildMain(), BorderLayout.CENTER);
        wireEditor();
        showEditor(null);
        setBusy(false, "");
    }

    // ───────────── Layout ─────────────

    private JComponent buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(WarnoTheme.SIDEBAR);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, WarnoTheme.BORDER));
        sidebar.setPreferredSize(new Dimension(340, 10));
        JPanel content = column();
        content.setBorder(WarnoTheme.pad(24, 24, 12, 20));

        JPanel brand = new JPanel(new BorderLayout(12, 0));
        brand.setOpaque(false);
        RoundedPanel mark = new RoundedPanel(new BorderLayout(), WarnoTheme.ACCENT_SOFT, WarnoTheme.ACCENT_SOFT_BORDER, 10);
        mark.setPreferredSize(new Dimension(40, 40));
        mark.add(WarnoTheme.icon(ICON_MAP, 18, WarnoTheme.ACCENT));
        brand.add(mark, BorderLayout.WEST);
        JPanel brandText = column();
        brandText.add(WarnoTheme.title("Maps", 19));
        brandText.add(WarnoTheme.caption("Game modes and map settings"));
        brand.add(brandText, BorderLayout.CENTER);
        add(content, brand, 20);

        add(content, WarnoTheme.sectionLabel("WARNO folder"), 8);
        add(content, row(warnoPathBox, browseButton, refreshButton), 24);

        add(content, WarnoTheme.sectionLabel("Output"), 8);
        add(content, WarnoTheme.caption("Mod name (an existing one gets the scenarios added)"), 6);
        add(content, modNameBox, 18);

        RoundedPanel about = new RoundedPanel(new BorderLayout(), null, WarnoTheme.BORDER, 8);
        about.setBorder(WarnoTheme.pad(14, 14, 14, 14));
        about.add(WarnoTheme.wrappingCaption(
            "Each ticked scenario is rebuilt as a new scenario with your settings. It keeps the map, zones, deployment "
                + "areas and the computer player's map data; the original stays as it is.\n\n"
                + "A zone's points are victory points in Conquest and income in Destruction, so a converted map plays "
                + "differently from Eugen's own version of that mode. Adjust them in the zone list.\n\n"
                + "The build runs WARNO's own map tools and mod tools. The scenarios appear in skirmish and "
                + "multiplayer while the mod is active."));
        add(content, about, 18);

        add(content, selectionText, 10);
        add(content, convertButton, 8);
        add(content, cancelButton, 12);
        add(content, progressText, 6);
        add(content, progressBar, 0);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(WarnoTheme.SIDEBAR);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sidebar.add(scroll, BorderLayout.CENTER);

        browseButton.addActionListener(e -> browse());
        refreshButton.addActionListener(e -> refresh());
        convertButton.addActionListener(e -> convert());
        cancelButton.addActionListener(e -> {
            requestCancel();
            cancelButton.setEnabled(false);
            progressText.setText("Cancelling, and waiting for WARNO's tools to finish…");
        });
        return sidebar;
    }

    private JComponent buildMain() {
        JPanel main = new JPanel(new BorderLayout(0, 12));
        main.setBackground(WarnoTheme.BACKGROUND);
        main.setBorder(WarnoTheme.pad(24, 24, 16, 24));

        JPanel header = column();
        header.add(WarnoTheme.title("Skirmish scenarios", 18));
        header.add(Box.createVerticalStrut(4));
        header.add(WarnoTheme.caption("Tick the scenarios to build, and select one to change its settings."));
        header.add(Box.createVerticalStrut(12));
        WarnoTheme.placeholder(searchBox, "Search maps");
        JPanel filters = new JPanel(new BorderLayout(8, 0));
        filters.setOpaque(false);
        filters.add(searchBox, BorderLayout.CENTER);
        filters.add(modeFilter, BorderLayout.EAST);
        filters.setAlignmentX(LEFT_ALIGNMENT);
        header.add(filters);
        main.add(header, BorderLayout.NORTH);

        table.setRowSorter(sorter);
        table.setRowHeight(30);
        table.setFillsViewportHeight(true);
        table.setShowVerticalLines(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setMaxWidth(44);
        table.getColumnModel().getColumn(1).setPreferredWidth(260);
        table.getColumnModel().getColumn(4).setPreferredWidth(280);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createLineBorder(WarnoTheme.BORDER));

        JSplitPane top = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, buildEditor());
        top.setResizeWeight(0.55);
        top.setBorder(BorderFactory.createEmptyBorder());

        logBox.setEditable(false);
        logBox.setFont(WarnoTheme.mono(12));
        logBox.setBackground(WarnoTheme.LOG);
        logBox.setForeground(WarnoTheme.LOG_TEXT);
        logBox.setBorder(WarnoTheme.pad(8, 10, 8, 10));
        JScrollPane logScroll = new JScrollPane(logBox);
        logScroll.setBorder(BorderFactory.createLineBorder(WarnoTheme.BORDER));
        logScroll.setPreferredSize(new Dimension(10, 160));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, logScroll);
        split.setResizeWeight(0.78);
        split.setBorder(BorderFactory.createEmptyBorder());
        main.add(split, BorderLayout.CENTER);

        DocumentListener filter = changes(this::applyFilter);
        searchBox.getDocument().addDocumentListener(filter);
        modeFilter.addActionListener(e -> applyFilter());
        model.addTableModelListener(e -> updateSelection());
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int view = table.getSelectedRow();
            showEditor(view < 0 ? null : model.rows.get(table.convertRowIndexToModel(view)));
        });
        return main;
    }

    private JComponent buildEditor() {
        editorBody.setLayout(new BoxLayout(editorBody, BoxLayout.Y_AXIS));
        editorBody.setBackground(WarnoTheme.PANEL);
        editorBody.setBorder(WarnoTheme.pad(16, 16, 16, 16));
        add(editorBody, editorTitle, 2);
        add(editorBody, editorSource, 14);

        add(editorBody, WarnoTheme.sectionLabel("Game mode"), 6);
        add(editorBody, modeBox, 12);
        add(editorBody, WarnoTheme.sectionLabel("Lobby name"), 6);
        add(editorBody, displayNameBox, 12);
        add(editorBody, WarnoTheme.sectionLabel("Scenario name (internal, letters, digits and _)"), 6);
        add(editorBody, scenarioNameBox, 12);
        add(editorBody, WarnoTheme.sectionLabel("Players (default open slots, both teams)"), 6);
        add(editorBody, slotsBox, 12);
        JPanel labels = new JPanel(new GridLayout(1, 2, 8, 0));
        labels.setOpaque(false);
        JPanel size = column();
        size.add(WarnoTheme.sectionLabel("Map size label"));
        size.add(Box.createVerticalStrut(6));
        size.add(sizeBox);
        JPanel density = column();
        density.add(WarnoTheme.sectionLabel("Density label"));
        density.add(Box.createVerticalStrut(6));
        density.add(densityBox);
        labels.add(size);
        labels.add(density);
        add(editorBody, labels, 10);
        riversBox.setOpaque(false);
        add(editorBody, riversBox, 12);

        add(editorBody, WarnoTheme.sectionLabel("Environments (times of day offered in the lobby)"), 6);
        environmentsPanel.setLayout(new BoxLayout(environmentsPanel, BoxLayout.Y_AXIS));
        environmentsPanel.setOpaque(false);
        add(editorBody, environmentsPanel, 12);

        add(editorBody, WarnoTheme.sectionLabel("Command zones and their points"), 6);
        zoneTable.setRowHeight(26);
        zoneTable.setFillsViewportHeight(true);
        JScrollPane zones = new JScrollPane(zoneTable);
        zones.setPreferredSize(new Dimension(10, 200));
        zones.setBorder(BorderFactory.createLineBorder(WarnoTheme.BORDER));
        zones.setAlignmentX(LEFT_ALIGNMENT);
        editorBody.add(zones);
        editorBody.add(Box.createVerticalStrut(8));
        JPanel scale = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        scale.setOpaque(false);
        scale.add(WarnoTheme.caption("Multiply every zone's points by"));
        scale.add(scaleBox);
        scale.add(WarnoTheme.caption("%"));
        scale.add(scaleButton);
        add(editorBody, scale, 14);
        add(editorBody, resetButton, 0);

        JScrollPane scroll = new JScrollPane(editorBody);
        scroll.setBorder(BorderFactory.createLineBorder(WarnoTheme.BORDER));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setMinimumSize(new Dimension(320, 10));
        return scroll;
    }

    // ───────────── Settings editor ─────────────

    private void wireEditor() {
        modeBox.addActionListener(e -> edit(s -> s.mode = (Mode) modeBox.getSelectedItem()));
        scenarioNameBox.getDocument().addDocumentListener(changes(() -> edit(s -> s.scenarioName = scenarioNameBox.getText().trim())));
        displayNameBox.getDocument().addDocumentListener(changes(() -> edit(s -> s.displayName = displayNameBox.getText())));
        slotsBox.addChangeListener(e -> edit(s -> s.slots = (Integer) slotsBox.getValue()));
        sizeBox.addActionListener(e -> edit(s -> s.mapSize = (String) sizeBox.getSelectedItem()));
        densityBox.addActionListener(e -> edit(s -> s.density = (String) densityBox.getSelectedItem()));
        riversBox.addActionListener(e -> edit(s -> s.rivers = riversBox.isSelected()));
        scaleButton.addActionListener(e -> {
            Settings s = editing == null ? null : settings.get(editing);
            if (s == null) return;
            int percent = (Integer) scaleBox.getValue();
            for (Zone zone : s.zones) s.zonePoints.put(zone.guid, Math.min(99, Math.round(s.points(zone) * percent / 100f)));
            zoneModel.fireTableDataChanged();
            log("Scaled " + editing.name + "'s zone points by " + percent + "%.");
        });
        resetButton.addActionListener(e -> {
            if (editing == null) return;
            Scenario scenario = editing;
            settings.remove(scenario);
            showEditor(scenario);
            model.refresh(scenario);
        });
    }

    private interface Change {
        void apply(Settings settings);
    }

    private void edit(Change change) {
        if (loadingEditor || editing == null) return;
        Settings s = settings.get(editing);
        if (s == null) return;
        change.apply(s);
        model.refresh(editing);
    }

    private Settings settingsFor(Scenario scenario) {
        Settings s = settings.get(scenario);
        if (s == null) {
            s = converter.defaults(scenario);
            settings.put(scenario, s);
        }
        return s;
    }

    private void showEditor(Scenario scenario) {
        editing = scenario;
        boolean on = scenario != null && !busy;
        for (Component c : new Component[]{modeBox, scenarioNameBox, displayNameBox, slotsBox, sizeBox, densityBox, riversBox, zoneTable, scaleBox, scaleButton, resetButton})
            c.setEnabled(on);
        environmentsPanel.removeAll();
        if (scenario == null) {
            editorTitle.setText("Choose a scenario");
            editorSource.setText("Its settings appear here.");
            zoneModel.show(null);
            environmentsPanel.revalidate();
            return;
        }
        Settings s;
        try {
            s = settingsFor(scenario);
        } catch (RuntimeException e) {
            editorTitle.setText(scenario.name);
            editorSource.setText("Its data couldn't be read: " + e.getMessage());
            zoneModel.show(null);
            return;
        }
        loadingEditor = true;
        try {
            editorTitle.setText(scenario.name);
            editorSource.setText(scenario.mode.label + " on map " + scenario.map + ", " + scenario.slots + " players, " + s.zones.size() + " command zones");
            modeBox.setSelectedItem(s.mode);
            scenarioNameBox.setText(s.scenarioName);
            displayNameBox.setText(s.displayName);
            slotsBox.setValue(s.slots);
            sizeBox.setSelectedItem(s.mapSize);
            densityBox.setSelectedItem(s.density);
            riversBox.setSelected(s.rivers);
            for (String environment : s.availableEnvironments) {
                JCheckBox box = new JCheckBox(environment.substring(environment.lastIndexOf('/') + 1), s.environments.contains(environment));
                box.setOpaque(false);
                box.setEnabled(on);
                box.addActionListener(e -> edit(settings -> {
                    settings.environments.clear();
                    for (Component c : environmentsPanel.getComponents()) {
                        JCheckBox b = (JCheckBox) c;
                        if (b.isSelected()) settings.environments.add(settings.availableEnvironments.get(environmentsPanel.getComponentZOrder(c)));
                    }
                }));
                environmentsPanel.add(box);
            }
            zoneModel.show(s);
        } finally {
            loadingEditor = false;
        }
        environmentsPanel.revalidate();
        environmentsPanel.repaint();
    }

    private final class ZoneModel extends AbstractTableModel {
        private Settings shown;

        void show(Settings s) {
            shown = s;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return shown == null ? 0 : shown.zones.size(); }
        @Override public int getColumnCount() { return 3; }
        @Override public String getColumnName(int c) { return c == 0 ? "Zone" : c == 1 ? "Source points" : "Points"; }
        @Override public Class<?> getColumnClass(int c) { return c == 0 ? String.class : Integer.class; }
        @Override public boolean isCellEditable(int r, int c) { return c == 2 && !busy; }

        @Override
        public Object getValueAt(int r, int c) {
            Zone zone = shown.zones.get(r);
            return c == 0 ? zone.name : c == 1 ? zone.points : shown.points(zone);
        }

        @Override
        public void setValueAt(Object value, int r, int c) {
            if (c != 2 || !(value instanceof Integer)) return;
            int points = Math.max(0, Math.min(99, (Integer) value));
            shown.zonePoints.put(shown.zones.get(r).guid, points);
            fireTableCellUpdated(r, c);
            if (editing != null) model.refresh(editing);
        }
    }

    // ───────────── Scenario list ─────────────

    private void applyFilter() {
        String text = searchBox.getText().trim().toLowerCase();
        int mode = modeFilter.getSelectedIndex();
        sorter.setRowFilter(new RowFilter<ScenarioModel, Integer>() {
            @Override
            public boolean include(Entry<? extends ScenarioModel, ? extends Integer> entry) {
                Scenario s = model.rows.get(entry.getIdentifier());
                if (mode == 1 && s.mode != Mode.CONQUEST) return false;
                if (mode == 2 && s.mode != Mode.DESTRUCTION) return false;
                return text.isEmpty() || s.name.toLowerCase().contains(text) || s.map.toLowerCase().contains(text);
            }
        });
    }

    private void updateSelection() {
        int count = model.selected().size();
        selectionText.setText(count == 0 ? "No scenarios ticked." : count + " scenario(s) ticked.");
        convertButton.setEnabled(!busy && count > 0 && paths != null);
    }

    /** Finds WARNO and lists its scenarios the first time the workspace is shown. */
    public void activate() {
        if (started) return;
        started = true;
        run("Finding WARNO", token -> {
            WarnoPaths detected = new WarnoLocator().locate();
            if (detected == null) {
                log("WARNO wasn't found in your Steam libraries. Choose its folder with the folder button.");
                return;
            }
            SwingUtilities.invokeLater(() -> warnoPathBox.setText(detected.warnoRoot.toString()));
            scan(detected, token);
        });
    }

    private void browse() {
        JFileChooser chooser = new JFileChooser(warnoPathBox.getText().trim().isEmpty() ? null : new File(warnoPathBox.getText().trim()));
        chooser.setDialogTitle("Select the WARNO installation folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        warnoPathBox.setText(chooser.getSelectedFile().getAbsolutePath());
        refresh();
    }

    private void refresh() {
        String folder = warnoPathBox.getText().trim();
        run("Reading scenarios", token -> scan(new WarnoLocator().fromWarnoRoot(folder), token));
    }

    private void scan(WarnoPaths found, Cancel token) {
        List<Scenario> scenarios = converter.scan(found.warnoRoot, token);
        long conquest = scenarios.stream().filter(s -> s.mode == Mode.CONQUEST).count();
        log("Found " + scenarios.size() + " skirmish scenarios (" + conquest + " Conquest, " + (scenarios.size() - conquest) + " Destruction) on "
            + scenarios.stream().map(s -> s.map).distinct().count() + " maps.");
        SwingUtilities.invokeLater(() -> {
            paths = found;
            settings.clear();
            model.set(scenarios);
            showEditor(null);
            updateSelection();
        });
    }

    private void convert() {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        if (zoneTable.isEditing()) zoneTable.getCellEditor().stopCellEditing();
        List<Scenario> chosen = model.selected();
        String modName = modNameBox.getText().trim();
        List<MapModeConverter.Job> jobs = new ArrayList<>();
        try {
            MapModeConverter.validateModName(modName);
            for (Scenario scenario : chosen) {
                Settings s = settingsFor(scenario);
                MapModeConverter.validate(scenario, s, model.rows);
                jobs.add(new MapModeConverter.Job(scenario, s));
            }
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Maps", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String list = jobs.stream().limit(12).map(j -> "  " + j.scenario.name + " → " + j.settings.scenarioName + " (" + j.settings.mode.label + ")")
            .collect(Collectors.joining("\n")) + (jobs.size() > 12 ? "\n  …and " + (jobs.size() - 12) + " more" : "");
        int answer = JOptionPane.showConfirmDialog(this, "Build these scenarios into the mod '" + modName + "'?\n\n" + list
            + "\n\nWARNO's tools run in the background; this takes a few minutes.", "Maps", JOptionPane.OK_CANCEL_OPTION);
        if (answer != JOptionPane.OK_OPTION) return;
        WarnoPaths current = paths;
        run("Building scenarios", token -> {
            List<String> built = converter.convert(new MapModeConverter.Request(current, jobs, modName), this::log,
                percent -> SwingUtilities.invokeLater(() -> progressBar.setValue(percent)), token);
            log("Done: " + built.size() + " scenario(s) in the mod '" + modName + "'. Activate it in WARNO's mod center to play them.");
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, built.size() + " scenario(s) were added to the mod '" + modName
                + "'.\nActivate it in WARNO's mod center; they appear in skirmish and multiplayer.", "Maps", JOptionPane.INFORMATION_MESSAGE));
        });
    }

    // ───────────── Plumbing ─────────────

    private interface Operation {
        void run(Cancel token) throws Exception;
    }

    private void run(String stage, Operation action) {
        if (busy) return;
        Cancel token = new Cancel();
        operation = token;
        setBusy(true, stage);
        worker.submit(() -> {
            try {
                action.run(token);
            } catch (CancellationException e) {
                log("Cancelled.");
            } catch (Throwable e) {
                Throwable cause = e.getCause() != null && !(e instanceof CombineException) ? e.getCause() : e;
                String message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
                log("Error: " + message);
                if (!(cause instanceof CombineException)) cause.printStackTrace();
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, message, "Maps", JOptionPane.ERROR_MESSAGE));
            } finally {
                operation = null;
                SwingUtilities.invokeLater(() -> setBusy(false, ""));
            }
        });
    }

    private void setBusy(boolean value, String stage) {
        busy = value;
        progressText.setText(stage);
        progressText.setVisible(value);
        progressBar.setVisible(value);
        progressBar.setValue(0);
        cancelButton.setVisible(value);
        cancelButton.setEnabled(value);
        browseButton.setEnabled(!value);
        refreshButton.setEnabled(!value);
        modNameBox.setEnabled(!value);
        table.setEnabled(!value);
        showEditor(editing);
        updateSelection();
    }

    public boolean isBusy() {
        return busy;
    }

    public void requestCancel() {
        Cancel current = operation;
        if (current != null) current.cancel();
    }

    public void shutdown() {
        requestCancel();
        worker.shutdownNow();
    }

    private void log(String message) {
        String line = "[" + LocalTime.now().format(CLOCK) + "] " + message + "\n";
        SwingUtilities.invokeLater(() -> {
            logBox.append(line);
            logBox.setCaretPosition(logBox.getDocument().getLength());
        });
    }

    private static DocumentListener changes(Runnable action) {
        return new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { action.run(); }
            public void removeUpdate(DocumentEvent e) { action.run(); }
            public void changedUpdate(DocumentEvent e) { action.run(); }
        };
    }

    private static JPanel column() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        return panel;
    }

    private static JPanel row(JComponent fill, JComponent... trailing) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.add(fill, BorderLayout.CENTER);
        JPanel right = new JPanel(new GridLayout(1, trailing.length, 6, 0));
        right.setOpaque(false);
        for (JComponent c : trailing) right.add(c);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    private static void add(JPanel column, JComponent component, int gapAfter) {
        component.setAlignmentX(LEFT_ALIGNMENT);
        if (!(component instanceof JTextArea) && !(component instanceof JLabel))
            component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        column.add(component);
        if (gapAfter > 0) column.add(Box.createVerticalStrut(gapAfter));
    }

    private final class ScenarioModel extends AbstractTableModel {
        private final String[] columns = {"", "Scenario", "Map", "Mode", "Builds as"};
        final List<Scenario> rows = new ArrayList<>();
        final List<Boolean> ticked = new ArrayList<>();

        void set(List<Scenario> scenarios) {
            rows.clear();
            ticked.clear();
            rows.addAll(scenarios);
            ticked.addAll(Collections.nCopies(scenarios.size(), false));
            fireTableDataChanged();
        }

        void refresh(Scenario scenario) {
            int row = rows.indexOf(scenario);
            if (row >= 0) fireTableRowsUpdated(row, row);
        }

        List<Scenario> selected() {
            List<Scenario> result = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) if (ticked.get(i)) result.add(rows.get(i));
            return result;
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public Class<?> getColumnClass(int column) { return column == 0 ? Boolean.class : String.class; }
        @Override public boolean isCellEditable(int row, int column) { return column == 0; }

        @Override
        public Object getValueAt(int row, int column) {
            Scenario s = rows.get(row);
            switch (column) {
                case 0: return ticked.get(row);
                case 1: return s.name;
                case 2: return s.map;
                case 3: return s.mode.label;
                default:
                    Settings edited = settings.get(s);
                    return edited != null ? edited.scenarioName + "  (" + edited.mode.label + ")" : s.convertedName() + "  (" + s.mode.other().label + ")";
            }
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column != 0) return;
            ticked.set(row, Boolean.TRUE.equals(value));
            fireTableCellUpdated(row, column);
        }
    }
}
