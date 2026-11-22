package com.warnomodmaker.gui.combine;

import com.warnomodmaker.combine.BuildRecovery;
import com.warnomodmaker.combine.Cancel;
import com.warnomodmaker.combine.CombinationHealth;
import com.warnomodmaker.combine.CombineException;
import com.warnomodmaker.combine.CombineService;
import com.warnomodmaker.combine.CombinedModRemover;
import com.warnomodmaker.combine.CombinedModStateStore;
import com.warnomodmaker.combine.CombinedModUpdater;
import com.warnomodmaker.combine.FileOps;
import com.warnomodmaker.combine.Json;
import com.warnomodmaker.combine.MergePlanner;
import com.warnomodmaker.combine.ModFingerprintService;
import com.warnomodmaker.combine.ModScanner;
import com.warnomodmaker.combine.Models.CombineProgress;
import com.warnomodmaker.combine.Models.CombineRequest;
import com.warnomodmaker.combine.Models.CombineResult;
import com.warnomodmaker.combine.Models.CombinedModState;
import com.warnomodmaker.combine.Models.MergeDecision;
import com.warnomodmaker.combine.Models.MergePreview;
import com.warnomodmaker.combine.Models.ModDescriptor;
import com.warnomodmaker.combine.Models.ModKind;
import com.warnomodmaker.combine.Models.SourceModFingerprint;
import com.warnomodmaker.combine.Models.WarnoPaths;
import com.warnomodmaker.combine.ProcessRunner;
import com.warnomodmaker.combine.SourceDeltaAnalyzer;
import com.warnomodmaker.combine.WarnoLocator;
import com.warnomodmaker.combine.ndf.IgnoreCase;
import com.warnomodmaker.combine.ndf.NdfFormatException;
import com.warnomodmaker.gui.theme.GlyphIcon;
import com.warnomodmaker.gui.theme.RoundedPanel;
import com.warnomodmaker.gui.theme.ToggleSwitch;
import com.warnomodmaker.gui.theme.WarnoTheme;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The Combine workspace: combines any installed Workshop or editable mods into one local mod by delta merging their
 * compiled databases (formerly WARNO UltiAI MODerator). The sidebar picks the mods and their priority; the main area
 * shows the merge plan before anything is built.
 */
public final class CombinePanel extends JPanel {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    // Fluent icon code points.
    private static final char ICON_FOLDER = '', ICON_REFRESH = '', ICON_ADD = '', ICON_UP = '', ICON_DOWN = '',
        ICON_DELETE = '', ICON_PRIMARY = '', ICON_COMBINE = '', ICON_UPDATE = '', ICON_SEARCH = '',
        ICON_EXPORT = '', ICON_LIBRARY = '', ICON_CHEVRON_RIGHT = '';

    // Engine
    private final ModScanner scanner = new ModScanner();
    private final MergePlanner planner = new MergePlanner(new SourceDeltaAnalyzer());
    private final ModFingerprintService fingerprints = new ModFingerprintService();
    private final CombinedModStateStore stateStore = new CombinedModStateStore();
    private final CombineService service = new CombineService(new SourceDeltaAnalyzer(), new ProcessRunner());
    private final CombinationHealth health = new CombinationHealth();
    private final CombinedModRemover remover = new CombinedModRemover();
    private final CombinedModUpdater updater = new CombinedModUpdater(service, planner);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> daemon(r, "combine-operation"));
    private final ExecutorService checker = Executors.newSingleThreadExecutor(r -> daemon(r, "combine-update-check"));

    // State (read and written on the event thread)
    private WarnoPaths paths;
    private List<ModDescriptor> installed = Collections.emptyList();
    private final List<ModDescriptor> order = new ArrayList<>();
    private List<CombinedModUpdater.Update> updates = Collections.emptyList();
    private List<CombinedModRemover.Targets> leftovers = Collections.emptyList();
    private volatile Cancel updateCheck;
    private volatile Cancel operation;
    private volatile boolean busy;
    private boolean settingSelection;
    private boolean started;
    private CombinedModState existing;
    private List<String> changedMods = Collections.emptyList();
    private String previewError;
    private MergePreview preview;
    private String lastLoggedPreviewKey = "";
    private String status = "Add at least two mods to combine.";

    // Sidebar
    private final JTextField warnoPathBox = new JTextField();
    private final JButton browseButton = WarnoTheme.iconButton(ICON_FOLDER, "Choose the WARNO folder");
    private final JButton refreshButton = WarnoTheme.iconButton(ICON_REFRESH, "Rescan installed mods");
    private final JComboBox<ModDescriptor> availableModBox = new JComboBox<>();
    private final JButton addModButton = WarnoTheme.iconButton(ICON_ADD, "Add the chosen mod");
    private final DefaultListModel<ModDescriptor> orderModel = new DefaultListModel<>();
    private final JList<ModDescriptor> mergeOrderList = new JList<>(orderModel);
    private final CardLayout orderCards = new CardLayout();
    private final JPanel orderHost = new JPanel(orderCards);
    private final JButton primaryButton = WarnoTheme.button("Make primary", ICON_PRIMARY);
    private final JButton upButton = WarnoTheme.iconButton(ICON_UP, "Move up");
    private final JButton downButton = WarnoTheme.iconButton(ICON_DOWN, "Move down");
    private final JButton removeButton = WarnoTheme.iconButton(ICON_DELETE, "Remove from the list");
    private final JTextField outputNameBox = new JTextField();
    private final JComboBox<CombinedModState> existingMergeBox = new JComboBox<>();
    private final JButton deleteMergeButton = WarnoTheme.iconButton(ICON_DELETE, "Delete this combined mod from both Mods and Saved Games, with its leftovers");
    private final JButton cleanLeftoversButton = new JButton();
    private final JLabel statusText = new JLabel();
    private final JPanel progressPanel = new JPanel(new BorderLayout(0, 8));
    private final JLabel progressText = new JLabel("Preparing");
    private final JProgressBar busyBar = new JProgressBar(0, 100);
    private final JButton combineButton = WarnoTheme.accentButton("Create combined mod", ICON_COMBINE);
    private final JButton updateButton = new JButton("Rebuild existing");
    private final JButton cancelButton = new JButton("Cancel");

    // Main area
    private final JLabel summaryText = WarnoTheme.caption("Every file the combined mod will contain, and how each one is produced.");
    private final JButton checkUpdatesButton = WarnoTheme.button("Check for updates", ICON_UPDATE);
    private final RoundedPanel updatesBanner = new RoundedPanel(new BorderLayout(14, 0), WarnoTheme.hex("#1D2416"), WarnoTheme.hex("#3F5A2E"), 10);
    private final JLabel updatesTitle = WarnoTheme.title("", 14);
    private final JPanel updatesList = new JPanel();
    private final JButton updateAllButton = WarnoTheme.accentButton("Update all", ICON_UPDATE);
    private final JButton updateWithoutOutdatedButton = new JButton("Update without outdated mods");
    private final JLabel statFiles = stat(WarnoTheme.TEXT), statMerged = stat(WarnoTheme.SUCCESS), statConflicts = stat(WarnoTheme.WARNING),
        statOverrides = stat(WarnoTheme.CAUTION), statSingle = stat(WarnoTheme.INFO), statIdentical = stat(WarnoTheme.MUTED);
    private final JTextField searchBox = new JTextField();
    private final ToggleSwitch conflictsOnlyBox = new ToggleSwitch("Conflicts only");
    private final JButton exportButton = WarnoTheme.button("Export report", ICON_EXPORT);
    private final PlanModel planModel = new PlanModel();
    private final JTable previewGrid = new JTable(planModel);
    private final CardLayout planCards = new CardLayout();
    private final JPanel planHost = new JPanel(planCards);
    private final JLabel emptyPlanTitle = WarnoTheme.title("No plan yet", 16);
    private final JTextArea emptyPlanText = WarnoTheme.wrappingCaption("");
    private final JTextArea logBox = new JTextArea();
    private final JPanel logBody = new JPanel(new BorderLayout());
    private final JLabel logChevron = WarnoTheme.icon('', 10, WarnoTheme.MUTED);

    public CombinePanel() {
        super(new BorderLayout());
        setBackground(WarnoTheme.BACKGROUND);
        add(buildSidebar(), BorderLayout.WEST);
        add(buildMain(), BorderLayout.CENTER);
        wire();
        clearPreview();
        setBusy(false, "Preparing");
    }

    private static Thread daemon(Runnable r, String name) {
        Thread thread = new Thread(null, r, name, 64L << 20);
        thread.setDaemon(true);
        return thread;
    }

    private static JLabel stat(Color color) {
        JLabel label = new JLabel("–");
        label.setFont(WarnoTheme.semibold(22));
        label.setForeground(color);
        return label;
    }

    // ───────────── Layout ─────────────

    private JComponent buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(WarnoTheme.SIDEBAR);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, WarnoTheme.BORDER));
        sidebar.setPreferredSize(new Dimension(380, 10));

        JPanel content = new ScrollablePanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(WarnoTheme.SIDEBAR);
        content.setBorder(WarnoTheme.pad(24, 24, 12, 20));

        JPanel brand = new JPanel(new BorderLayout(12, 0));
        brand.setOpaque(false);
        RoundedPanel mark = new RoundedPanel(new BorderLayout(), WarnoTheme.ACCENT_SOFT, WarnoTheme.ACCENT_SOFT_BORDER, 10);
        mark.setPreferredSize(new Dimension(40, 40));
        mark.add(WarnoTheme.icon(ICON_LIBRARY, 18, WarnoTheme.ACCENT));
        brand.add(mark, BorderLayout.WEST);
        JPanel brandText = column();
        brandText.add(WarnoTheme.title("Combine mods", 19));
        brandText.add(WarnoTheme.caption("Merge any installed WARNO mods into one"));
        brand.add(brandText, BorderLayout.CENTER);
        add(content, brand, 20);

        add(content, WarnoTheme.sectionLabel("WARNO folder"), 8);
        warnoPathBox.setToolTipText("The folder WARNO is installed in");
        add(content, row(warnoPathBox, browseButton, refreshButton), 24);

        JPanel modsHeader = new JPanel(new BorderLayout());
        modsHeader.setOpaque(false);
        modsHeader.add(WarnoTheme.sectionLabel("Mods"), BorderLayout.WEST);
        JLabel wins = WarnoTheme.caption("Higher in the list wins conflicts");
        wins.setFont(WarnoTheme.ui(11));
        modsHeader.add(wins, BorderLayout.EAST);
        add(content, modsHeader, 8);
        availableModBox.setRenderer(new ModComboRenderer());
        availableModBox.setMaximumRowCount(12);
        availableModBox.setPreferredSize(new Dimension(10, 44));
        addModButton.setPreferredSize(new Dimension(44, 44));
        addModButton.setMinimumSize(new Dimension(44, 44));
        add(content, row(availableModBox, addModButton), 10);

        mergeOrderList.setCellRenderer(new OrderRenderer());
        mergeOrderList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        mergeOrderList.setOpaque(false);
        mergeOrderList.setBackground(WarnoTheme.SIDEBAR);
        mergeOrderList.setVisibleRowCount(-1);
        orderHost.setOpaque(false);
        orderHost.add(mergeOrderList, "list");
        RoundedPanel hint = new RoundedPanel(new BorderLayout(), null, WarnoTheme.BORDER, 8);
        hint.setBorder(WarnoTheme.pad(14, 14, 14, 14));
        JTextArea hintText = WarnoTheme.wrappingCaption("Pick a mod above and add it. Add at least two to combine them.");
        hint.add(hintText);
        orderHost.add(hint, "hint");
        add(content, orderHost, 8);

        JPanel orderButtons = new JPanel(new BorderLayout(6, 0));
        orderButtons.setOpaque(false);
        primaryButton.setToolTipText("Move the selected mod to the top so it wins every conflict");
        orderButtons.add(primaryButton, BorderLayout.CENTER);
        JPanel moves = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        moves.setOpaque(false);
        moves.add(upButton);
        moves.add(downButton);
        moves.add(removeButton);
        orderButtons.add(moves, BorderLayout.EAST);
        add(content, orderButtons, 24);

        add(content, WarnoTheme.sectionLabel("Output"), 8);
        add(content, WarnoTheme.caption("New mod name"), 6);
        add(content, outputNameBox, 14);
        add(content, WarnoTheme.caption("Or rebuild an existing combined mod"), 6);
        deleteMergeButton.setForeground(WarnoTheme.DANGER);
        add(content, row(existingMergeBox, deleteMergeButton), 10);
        cleanLeftoversButton.setFont(WarnoTheme.ui(12));
        cleanLeftoversButton.setToolTipText("Delete folders left behind by interrupted or cancelled builds");
        cleanLeftoversButton.setVisible(false);
        add(content, cleanLeftoversButton, 24);

        add(content, WarnoTheme.sectionLabel("Credits"), 8);
        JTextArea credits = WarnoTheme.wrappingCaption("Every mod you combine remains the work of its original author, who keeps full credit for it. "
            + "This tool only combines mods already installed on your PC, adjusting their files where WARNO requires it; its author takes no "
            + "credit for, and claims no ownership of, any mod it combines or changes. Please support the original authors on the Steam Workshop.");
        credits.setFont(WarnoTheme.ui(11.5f));
        credits.setForeground(WarnoTheme.FAINT);
        add(content, credits, 8);

        JScrollPane scroll = new JScrollPane(content, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(WarnoTheme.SIDEBAR);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        sidebar.add(scroll, BorderLayout.CENTER);

        JPanel actions = column();
        actions.setOpaque(true);
        actions.setBackground(WarnoTheme.SIDEBAR);
        actions.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, WarnoTheme.BORDER), WarnoTheme.pad(16, 24, 22, 24)));
        statusText.setFont(WarnoTheme.ui(12));
        statusText.setForeground(WarnoTheme.MUTED);
        add(actions, statusText, 12);
        progressPanel.setOpaque(false);
        progressText.setFont(WarnoTheme.ui(12));
        progressText.setForeground(WarnoTheme.TEXT);
        busyBar.setPreferredSize(new Dimension(10, 6));
        progressPanel.add(progressText, BorderLayout.NORTH);
        progressPanel.add(busyBar, BorderLayout.CENTER);
        add(actions, progressPanel, 12);
        add(actions, combineButton, 8);
        cancelButton.setForeground(WarnoTheme.DANGER);
        JPanel secondary = new JPanel(new BorderLayout(8, 0));
        secondary.setOpaque(false);
        secondary.add(updateButton, BorderLayout.CENTER);
        secondary.add(cancelButton, BorderLayout.EAST);
        add(actions, secondary, 0);
        sidebar.add(actions, BorderLayout.SOUTH);
        return sidebar;
    }

    private JComponent buildMain() {
        JPanel main = new JPanel(new BorderLayout(0, 0));
        main.setBackground(WarnoTheme.BACKGROUND);
        main.setBorder(WarnoTheme.pad(24, 28, 24, 28));

        JPanel top = column();
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel titles = column();
        titles.add(WarnoTheme.title("Merge plan", 26));
        titles.add(Box.createVerticalStrut(4));
        titles.add(summaryText);
        header.add(titles, BorderLayout.CENTER);
        checkUpdatesButton.setToolTipText("Check every combined mod against its source mods and the installed WARNO");
        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        headerRight.setOpaque(false);
        headerRight.add(checkUpdatesButton);
        header.add(headerRight, BorderLayout.EAST);
        top.add(header);

        updatesBanner.setBorder(WarnoTheme.pad(12, 16, 12, 16));
        updatesBanner.add(WarnoTheme.icon(ICON_UPDATE, 18, WarnoTheme.ACCENT), BorderLayout.WEST);
        JPanel bannerText = column();
        bannerText.add(updatesTitle);
        updatesList.setLayout(new BoxLayout(updatesList, BoxLayout.Y_AXIS));
        updatesList.setOpaque(false);
        bannerText.add(updatesList);
        updatesBanner.add(bannerText, BorderLayout.CENTER);
        JPanel bannerButtons = column();
        updateAllButton.setToolTipText("Rebuild every out-of-date combined mod from its source mods, in its priority order");
        updateWithoutOutdatedButton.setToolTipText("Rebuild combined mods that have outdated source mods, leaving those mods out until their authors update them");
        bannerButtons.add(updateAllButton);
        bannerButtons.add(Box.createVerticalStrut(8));
        bannerButtons.add(updateWithoutOutdatedButton);
        updatesBanner.add(bannerButtons, BorderLayout.EAST);
        updatesBanner.setVisible(false);
        updatesBanner.setAlignmentX(LEFT_ALIGNMENT);
        top.add(Box.createVerticalStrut(16));
        top.add(updatesBanner);
        top.add(Box.createVerticalStrut(18));

        JPanel tiles = new JPanel(new GridLayout(1, 6, 10, 0));
        tiles.setOpaque(false);
        tiles.add(tile("Files", statFiles));
        tiles.add(tile("Delta merged", statMerged));
        tiles.add(tile("Value conflicts", statConflicts));
        tiles.add(tile("Priority overrides", statOverrides));
        tiles.add(tile("From one mod", statSingle));
        tiles.add(tile("Identical", statIdentical));
        tiles.setAlignmentX(LEFT_ALIGNMENT);
        top.add(tiles);
        top.add(Box.createVerticalStrut(18));

        JPanel filters = new JPanel(new BorderLayout(16, 0));
        filters.setOpaque(false);
        searchBox.setToolTipText("Filter by file or value path, decision, winning mod or reason");
        WarnoTheme.placeholder(searchBox, "Search files, values, mods");
        if (WarnoTheme.hasIcons()) searchBox.putClientProperty("JTextField.leadingIcon", new GlyphIcon(ICON_SEARCH, 13, WarnoTheme.FAINT));
        searchBox.setPreferredSize(new Dimension(460, 34));
        filters.add(searchBox, BorderLayout.WEST);
        JPanel filterRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
        filterRight.setOpaque(false);
        filterRight.add(conflictsOnlyBox);
        exportButton.setToolTipText("Save the full plan as a JSON report");
        filterRight.add(exportButton);
        filters.add(filterRight, BorderLayout.EAST);
        filters.setAlignmentX(LEFT_ALIGNMENT);
        top.add(filters);
        top.add(Box.createVerticalStrut(12));
        main.add(top, BorderLayout.NORTH);

        // Plan table
        previewGrid.setFillsViewportHeight(true);
        previewGrid.setRowHeight(34);
        previewGrid.setShowVerticalLines(false);
        previewGrid.getTableHeader().setReorderingAllowed(false);
        previewGrid.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        configureColumns();
        JScrollPane gridScroll = new JScrollPane(previewGrid);
        gridScroll.setBorder(BorderFactory.createEmptyBorder());
        gridScroll.getViewport().setBackground(WarnoTheme.PANEL);
        planHost.setOpaque(false);
        planHost.add(gridScroll, "grid");
        planHost.add(emptyPlan(), "empty");
        RoundedPanel card = new RoundedPanel(new BorderLayout(), WarnoTheme.PANEL, WarnoTheme.BORDER, 10);
        card.setBorder(WarnoTheme.pad(8, 6, 6, 6));
        card.add(planHost);
        main.add(card, BorderLayout.CENTER);

        // Activity
        JPanel activity = new JPanel(new BorderLayout(0, 8));
        activity.setOpaque(false);
        activity.setBorder(WarnoTheme.pad(14, 0, 0, 0));
        JPanel logHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        logHeader.setOpaque(false);
        logHeader.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logHeader.add(logChevron);
        logHeader.add(WarnoTheme.sectionLabel("Activity"));
        logHeader.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                logBody.setVisible(!logBody.isVisible());
                logChevron.setText(String.valueOf(logBody.isVisible() ? '' : ICON_CHEVRON_RIGHT));
                revalidate();
            }
        });
        activity.add(logHeader, BorderLayout.NORTH);
        logBox.setEditable(false);
        logBox.setLineWrap(true);
        logBox.setWrapStyleWord(true);
        logBox.setFont(WarnoTheme.mono(11.5f));
        logBox.setForeground(WarnoTheme.LOG_TEXT);
        logBox.setBackground(WarnoTheme.LOG);
        logBox.setBorder(WarnoTheme.pad(10, 14, 10, 14));
        JScrollPane logScroll = new JScrollPane(logBox);
        logScroll.setBorder(BorderFactory.createEmptyBorder());
        logScroll.getViewport().setBackground(WarnoTheme.LOG);
        RoundedPanel logCard = new RoundedPanel(new BorderLayout(), WarnoTheme.LOG, WarnoTheme.BORDER, 10);
        logCard.setBorder(WarnoTheme.pad(2, 2, 2, 2));
        logCard.add(logScroll);
        logBody.setOpaque(false);
        logBody.setPreferredSize(new Dimension(10, 150));
        logBody.add(logCard);
        activity.add(logBody, BorderLayout.CENTER);
        main.add(activity, BorderLayout.SOUTH);
        return main;
    }

    private JComponent emptyPlan() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setOpaque(false);
        JPanel stack = column();
        RoundedPanel box = new RoundedPanel(new BorderLayout(), WarnoTheme.RAISED, null, 16);
        box.setPreferredSize(new Dimension(56, 56));
        box.setMaximumSize(new Dimension(56, 56));
        box.add(WarnoTheme.icon(ICON_LIBRARY, 22, WarnoTheme.MUTED));
        box.setAlignmentX(CENTER_ALIGNMENT);
        stack.add(box);
        stack.add(Box.createVerticalStrut(14));
        emptyPlanTitle.setAlignmentX(CENTER_ALIGNMENT);
        stack.add(emptyPlanTitle);
        stack.add(Box.createVerticalStrut(4));
        emptyPlanText.setColumns(32);
        emptyPlanText.setAlignmentX(CENTER_ALIGNMENT);
        emptyPlanText.setMaximumSize(new Dimension(380, 200));
        stack.add(emptyPlanText);
        stack.setPreferredSize(new Dimension(380, 170));
        outer.add(stack);
        return outer;
    }

    private static JComponent tile(String caption, JLabel value) {
        RoundedPanel tile = new RoundedPanel(new BorderLayout(0, 2), WarnoTheme.PANEL, WarnoTheme.BORDER, 10);
        tile.setBorder(WarnoTheme.pad(12, 14, 12, 14));
        tile.add(WarnoTheme.caption(caption), BorderLayout.NORTH);
        tile.add(value, BorderLayout.CENTER);
        return tile;
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
        if (trailing.length > 0) {
            JPanel right = new JPanel(new GridLayout(1, trailing.length, 6, 0));
            right.setOpaque(false);
            for (JComponent c : trailing) right.add(c);
            row.add(right, BorderLayout.EAST);
        }
        return row;
    }

    private static void add(JPanel column, JComponent component, int gapAfter) {
        component.setAlignmentX(LEFT_ALIGNMENT);
        if (!(component instanceof JTextArea) && !(component instanceof JLabel))
            component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        column.add(component);
        if (gapAfter > 0) column.add(Box.createVerticalStrut(gapAfter));
    }

    private void configureColumns() {
        TableColumn file = previewGrid.getColumnModel().getColumn(0);
        file.setPreferredWidth(420);
        file.setCellRenderer(new PaddedRenderer(WarnoTheme.mono(12), WarnoTheme.TEXT));
        TableColumn decision = previewGrid.getColumnModel().getColumn(1);
        decision.setPreferredWidth(150);
        decision.setMinWidth(150);
        decision.setMaxWidth(170);
        decision.setCellRenderer(new BadgeRenderer());
        TableColumn from = previewGrid.getColumnModel().getColumn(2);
        from.setPreferredWidth(190);
        from.setCellRenderer(new PaddedRenderer(WarnoTheme.ui(13), WarnoTheme.TEXT));
        TableColumn why = previewGrid.getColumnModel().getColumn(3);
        why.setPreferredWidth(540);
        why.setCellRenderer(new PaddedRenderer(WarnoTheme.ui(13), WarnoTheme.MUTED));
        javax.swing.table.TableRowSorter<PlanModel> sorter = new javax.swing.table.TableRowSorter<>(planModel);
        sorter.setComparator(1, (a, b) -> ((MergeDecision) a).actionLabel().compareTo(((MergeDecision) b).actionLabel()));
        previewGrid.setRowSorter(sorter);
    }

    // ───────────── Events ─────────────

    private void wire() {
        browseButton.addActionListener(e -> browse());
        refreshButton.addActionListener(e -> runUi("Refreshing mods", this::refreshMods));
        addModButton.addActionListener(e -> {
            ModDescriptor mod = (ModDescriptor) availableModBox.getSelectedItem();
            if (mod != null) changeOrder(() -> order.add(mod), mod);
        });
        primaryButton.addActionListener(e -> {
            ModDescriptor mod = mergeOrderList.getSelectedValue();
            if (mod != null) changeOrder(() -> {
                order.remove(mod);
                order.add(0, mod);
            }, mod);
        });
        upButton.addActionListener(e -> move(-1));
        downButton.addActionListener(e -> move(1));
        removeButton.addActionListener(e -> {
            ModDescriptor mod = mergeOrderList.getSelectedValue();
            if (mod != null) changeOrder(() -> order.remove(mod), null);
        });
        mergeOrderList.addListSelectionListener(e -> updateOrderButtons());
        availableModBox.addActionListener(e -> updateOrderButtons());
        outputNameBox.getDocument().addDocumentListener(onChange(() -> {
            if (!settingSelection) updateActionStates();
        }));
        existingMergeBox.addActionListener(e -> {
            if (!settingSelection && !busy) runUi("Checking existing merge", this::checkExisting);
        });
        deleteMergeButton.addActionListener(e -> deleteMerge());
        cleanLeftoversButton.addActionListener(e -> cleanLeftovers());
        combineButton.addActionListener(e -> runUi("Planning merge", token -> build(false, token)));
        updateButton.addActionListener(e -> runUi("Planning rebuild", token -> build(true, token)));
        cancelButton.addActionListener(e -> requestCancel());
        checkUpdatesButton.addActionListener(e -> checkUpdates());
        updateAllButton.addActionListener(e -> runUpdates(updates.stream().filter(CombinedModUpdater.Update::canUpdate).collect(Collectors.toList()), false));
        updateWithoutOutdatedButton.addActionListener(e -> updateWithoutOutdated());
        searchBox.getDocument().addDocumentListener(onChange(this::applyPreviewFilter));
        conflictsOnlyBox.addActionListener(e -> applyPreviewFilter());
        exportButton.addActionListener(e -> export());
    }

    private static DocumentListener onChange(Runnable action) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                action.run();
            }
        };
    }

    /** Finds WARNO and scans the mods the first time the workspace is shown. */
    public void activate() {
        if (started) return;
        started = true;
        runUi("Finding WARNO", token -> {
            WarnoPaths detected = new WarnoLocator().locate();
            if (detected == null) {
                status = "WARNO wasn't found in your Steam libraries. Choose its folder with the folder button.";
                return;
            }
            edt(() -> showWarnoPath(detected.warnoRoot.toString()));
            refreshMods(token);
        });
    }

    private void browse() {
        JFileChooser chooser = new JFileChooser(warnoPathBox.getText().trim().isEmpty() ? null : new File(warnoPathBox.getText().trim()));
        chooser.setDialogTitle("Select the WARNO installation folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        showWarnoPath(chooser.getSelectedFile().getAbsolutePath());
        runUi("Refreshing mods", this::refreshMods);
    }

    private void refreshMods(Cancel token) {
        String folder = edt(() -> warnoPathBox.getText().trim());
        List<Path> previousOrder = edt(() -> order.stream().map(m -> m.rootPath).collect(Collectors.toList()));
        WarnoPaths found = new WarnoLocator().fromWarnoRoot(folder);
        BuildRecovery recovery = new BuildRecovery();
        for (BuildRecovery.Record record : recovery.find(found)) {
            token.check();
            if (!record.committed && !confirm("An interrupted build of '" + record.outputName + "' was found. Restore the previous outputs now? The incomplete output will be deleted.",
                "Recover interrupted build", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)) {
                log("[RECOVERY] Recovery pending for " + record.outputName + "; rebuild remains blocked until it is recovered.");
                continue;
            }
            recovery.recover(found, record, message -> log("[RECOVERY] " + message));
        }
        List<ModDescriptor> mods = scanner.scan(found, token);
        List<CombinedModState> existingStates = stateStore.findAll(found, token);
        // UltiAI is always listed first; everything else follows alphabetically.
        List<ModDescriptor> sorted = new ArrayList<>(mods);
        sorted.sort((a, b) -> {
            int ulti = Boolean.compare(isUlti(b), isUlti(a));
            if (ulti != 0) return ulti;
            int workshop = Boolean.compare(isUlti(b) && b.kind == ModKind.WORKSHOP_COMPILED, isUlti(a) && a.kind == ModKind.WORKSHOP_COMPILED);
            return workshop != 0 ? workshop : IgnoreCase.ORDER.compare(a.name, b.name);
        });
        edt(() -> {
            paths = found;
            installed = sorted;
            List<ModDescriptor> restored = new ArrayList<>();
            for (Path root : previousOrder) {
                ModDescriptor mod = find(root);
                if (mod != null) restored.add(mod);
            }
            if (restored.isEmpty()) {
                for (CombinedModState state : existingStates) {
                    List<ModDescriptor> sources = new ArrayList<>();
                    for (SourceModFingerprint source : state.sources()) {
                        ModDescriptor mod = findSource(source.rootPath);
                        if (mod != null) sources.add(mod);
                    }
                    if (sources.size() == state.sources().size()) {
                        restored = sources;
                        break;
                    }
                }
            }
            if (restored.isEmpty()) installed.stream().filter(CombinePanel::isUlti).findFirst().ifPresent(restored::add);
            order.clear();
            order.addAll(restored);
            refreshModLists(null);
        });
        log(String.format("[SCAN] Found %d Workshop and %d editable SDK mods.",
            mods.stream().filter(m -> m.kind == ModKind.WORKSHOP_COMPILED).count(), mods.stream().filter(m -> m.kind == ModKind.EDITABLE_SOURCE).count()));
        findLeftovers(found);
        checkSelection(token);
        edt(this::startUpdateCheck);
    }

    /** Checks every combined mod in the background, so a scan never waits on hashing large mods. */
    private void startUpdateCheck() {
        if (paths == null) return;
        if (updateCheck != null) updateCheck.cancel();
        Cancel check = new Cancel();
        updateCheck = check;
        WarnoPaths p = paths;
        List<ModDescriptor> mods = installed;
        checker.submit(() -> {
            try {
                List<CombinedModUpdater.Update> found = updater.check(p, mods, null, check);
                if (!check.isCancelled()) SwingUtilities.invokeLater(() -> showUpdates(found));
            } catch (CancellationException ignored) {
                // A newer check replaced this one.
            } catch (CombineException | UncheckedIOException | NdfFormatException e) {
                log("[UPDATES] Couldn't check combined mods: " + e.getMessage());
            } finally {
                if (updateCheck == check) updateCheck = null;
            }
        });
    }

    private void showUpdates(List<CombinedModUpdater.Update> found) {
        updates = found;
        List<CombinedModUpdater.Update> pending = found.stream().filter(u -> !u.upToDate()).collect(Collectors.toList());
        updatesBanner.setVisible(!pending.isEmpty());
        updatesList.removeAll();
        for (CombinedModUpdater.Update u : pending) {
            JLabel line = new JLabel("<html><span style='color:#E8EAEF'>" + escape(u.outputName()) + "</span> · " + escape(u.status()) + "</html>");
            line.setFont(WarnoTheme.ui(12));
            line.setForeground(WarnoTheme.MUTED);
            line.setBorder(WarnoTheme.pad(2, 0, 0, 0));
            updatesList.add(line);
        }
        long ready = pending.stream().filter(CombinedModUpdater.Update::canUpdate).count();
        long waiting = pending.stream().filter(u -> !u.outdated.isEmpty()).count();
        if (ready > 0 && waiting == 0) updatesTitle.setText(ready + " combined mod" + (ready == 1 ? " is" : "s are") + " out of date");
        else if (ready == 0 && waiting > 0) updatesTitle.setText(waiting + " combined mod" + (waiting == 1 ? " has" : "s have") + " source mods that aren't up to date");
        else updatesTitle.setText(ready + " combined mod" + (ready == 1 ? "" : "s") + " can be updated; " + waiting + " " + (waiting == 1 ? "has" : "have") + " outdated source mods");
        updateAllButton.setText(ready == 1 ? "Update" : "Update " + ready);
        updateWithoutOutdatedButton.setVisible(pending.stream().anyMatch(CombinedModUpdater.Update::canUpdateWithoutOutdated));
        for (CombinedModUpdater.Update u : pending) log("[UPDATES] '" + u.outputName() + "': " + u.status());
        updateActionStates();
        revalidate();
        repaint();
    }

    private void checkUpdates() {
        if (paths == null) return;
        WarnoPaths p = paths;
        List<ModDescriptor> mods = installed;
        runUi("Checking combined mods for updates", token -> {
            if (updateCheck != null) updateCheck.cancel();
            List<CombinedModUpdater.Update> found = updater.check(p, mods, progressReporter(), token);
            edt(() -> showUpdates(found));
            if (found.isEmpty()) status = "No combined mods yet.";
            else if (found.stream().allMatch(CombinedModUpdater.Update::upToDate)) {
                status = "All " + found.size() + " combined mod(s) are up to date.";
                log("[UPDATES] " + status);
            }
        });
    }

    private void updateWithoutOutdated() {
        List<CombinedModUpdater.Update> targets = updates.stream().filter(CombinedModUpdater.Update::canUpdateWithoutOutdated).collect(Collectors.toList());
        if (targets.isEmpty()) return;
        String lines = targets.stream().map(u -> "• " + u.outputName() + ": leaves out " + u.outdated.stream().map(o -> o.name).collect(Collectors.joining(", "))
            + "; keeps " + u.sources.stream().map(m -> m.name).collect(Collectors.joining(" > "))).collect(Collectors.joining("\n"));
        if (JOptionPane.showConfirmDialog(this, "Rebuild these combined mods without their outdated source mods?\n\n" + lines
                + "\n\nThe left-out mods' changes won't be in the combined mod until you add them again after their authors update them.",
            "Update without outdated mods", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) return;
        runUpdates(targets, true);
    }

    private void runUpdates(List<CombinedModUpdater.Update> targets, boolean withoutOutdated) {
        if (paths == null || targets.isEmpty()) return;
        WarnoPaths p = paths;
        runUi("Updating combined mods", token -> {
            if (updateCheck != null) updateCheck.cancel();
            for (int i = 0; i < targets.size(); i++) {
                CombinedModUpdater.Update update = targets.get(i);
                String prefix = targets.size() == 1 ? "Updating '" + update.outputName() + "'" : "Updating " + (i + 1) + " of " + targets.size() + ": '" + update.outputName() + "'";
                log("[UPDATE] " + prefix + " (" + update.status() + ")");
                Consumer<CombineProgress> progress = progressReporter();
                updater.update(p, update, withoutOutdated, this::log, x -> progress.accept(new CombineProgress(x.percent, prefix + " · " + x.stage)), token);
            }
            status = targets.size() == 1 ? "'" + targets.get(0).outputName() + "' is up to date." : targets.size() + " combined mods updated.";
            log("[UPDATE] " + status);
            List<CombinedModUpdater.Update> found = updater.check(p, edt(() -> installed), null, token);
            edt(() -> showUpdates(found));
            checkSelection(token);
        });
    }

    private void findLeftovers(WarnoPaths p) {
        List<CombinedModRemover.Targets> found = remover.findLeftovers(p);
        int folders = found.stream().mapToInt(l -> l.folders.size()).sum();
        edt(() -> {
            leftovers = found;
            cleanLeftoversButton.setText("Remove " + folders + " leftover folder" + (folders == 1 ? "" : "s") + " from interrupted builds");
            cleanLeftoversButton.setVisible(folders > 0);
        });
    }

    private void deleteMerge() {
        CombinedModState state = (CombinedModState) existingMergeBox.getSelectedItem();
        if (paths == null || state == null) return;
        WarnoPaths p = paths;
        runUi("Deleting combined mod", token -> {
            CombinedModRemover.Targets files = remover.find(p, state.outputName);
            if (!confirm("Delete the combined mod '" + files.outputName + "'?\n\nThese folders will be permanently deleted:\n\n"
                    + files.folders.stream().map(Path::toString).collect(Collectors.joining("\n")) + "\n\nYour other mods are not touched. This can't be undone.",
                "Delete combined mod", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE)) return;
            remover.delete(p, files, message -> log("[DELETE] " + message));
            status = "Deleted '" + files.outputName + "' from both locations.";
            findLeftovers(p);
            checkSelection(token);
        });
    }

    private void cleanLeftovers() {
        if (paths == null || leftovers.isEmpty()) return;
        WarnoPaths p = paths;
        List<CombinedModRemover.Targets> targets = leftovers;
        runUi("Removing leftovers", token -> {
            if (!confirm("Delete the folders left behind by interrupted or cancelled builds?\n\n"
                    + targets.stream().flatMap(l -> l.folders.stream()).map(Path::toString).collect(Collectors.joining("\n")) + "\n\nThis can't be undone.",
                "Remove leftovers", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE)) return;
            for (CombinedModRemover.Targets files : targets) remover.delete(p, files, message -> log("[DELETE] " + message));
            status = "Leftover folders removed.";
            findLeftovers(p);
        });
    }

    private ModDescriptor find(Path root) {
        for (ModDescriptor m : installed) if (FileOps.samePath(m.rootPath, root)) return m;
        return null;
    }

    // A combined mod's recorded source, even after Steam moved WARNO to another library.
    private ModDescriptor findSource(String root) {
        for (ModDescriptor m : installed) if (CombinedModStateStore.sameSource(root, m.rootPath.toString())) return m;
        return null;
    }

    private void refreshModLists(ModDescriptor select) {
        settingSelection = true;
        try {
            orderModel.clear();
            for (ModDescriptor m : order) orderModel.addElement(m);
            if (select != null) mergeOrderList.setSelectedIndex(order.indexOf(select));
            DefaultComboBoxModel<ModDescriptor> available = new DefaultComboBoxModel<>();
            for (ModDescriptor m : installed) if (order.stream().noneMatch(o -> FileOps.samePath(o.rootPath, m.rootPath))) available.addElement(m);
            availableModBox.setModel(available);
            availableModBox.setSelectedIndex(available.getSize() > 0 ? 0 : -1);
            orderCards.show(orderHost, order.isEmpty() ? "hint" : "list");
            orderHost.setPreferredSize(new Dimension(10, order.isEmpty() ? 64 : Math.max(64, order.size() * 56)));
            orderHost.setMaximumSize(new Dimension(Integer.MAX_VALUE, orderHost.getPreferredSize().height));
            orderHost.revalidate();
        } finally {
            settingSelection = false;
        }
        updateOrderButtons();
    }

    private void changeOrder(Runnable change, ModDescriptor select) {
        if (busy) return;
        change.run();
        refreshModLists(select);
        runUi("Checking selected mods", this::checkSelection);
    }

    private void move(int delta) {
        ModDescriptor mod = mergeOrderList.getSelectedValue();
        if (mod == null) return;
        int index = order.indexOf(mod), target = index + delta;
        if (target < 0 || target >= order.size()) return;
        changeOrder(() -> {
            order.remove(index);
            order.add(target, mod);
        }, mod);
    }

    private void updateOrderButtons() {
        ModDescriptor selected = mergeOrderList.getSelectedValue();
        int index = selected != null ? order.indexOf(selected) : -1;
        primaryButton.setEnabled(!busy && index > 0);
        upButton.setEnabled(!busy && index > 0);
        downButton.setEnabled(!busy && index >= 0 && index < order.size() - 1);
        removeButton.setEnabled(!busy && index >= 0);
        addModButton.setEnabled(!busy && availableModBox.getSelectedItem() != null);
    }

    private String defaultOutputName() {
        return order.stream().map(m -> m.name).collect(Collectors.joining(" + "));
    }

    private void checkSelection(Cancel token) {
        WarnoPaths p = edt(() -> {
            existing = null;
            changedMods = Collections.emptyList();
            previewError = null;
            clearPreview();
            status = "Add at least two mods to combine.";
            settingSelection = true;
            try {
                existingMergeBox.setModel(new DefaultComboBoxModel<>());
            } finally {
                settingSelection = false;
            }
            return order.size() < 2 ? null : paths;
        });
        if (p == null) return;
        List<ModDescriptor> mods = edt(() -> new ArrayList<>(order));
        String defaultName = edt(this::defaultOutputName);
        List<CombinedModState> choices = new ArrayList<>(stateStore.findAllForSources(p, mods, token));
        if (MergePlanner.isValidOutputName(defaultName) && choices.stream().noneMatch(x -> x.outputName.equalsIgnoreCase(defaultName))
            && CombinedModStateStore.outputExists(p, defaultName)) {
            List<SourceModFingerprint> sources = mods.stream().map(m -> new SourceModFingerprint(m.name, m.rootPath.toString(), "")).collect(Collectors.toList());
            choices.add(new CombinedModState(CombinedModState.CURRENT_SCHEMA_VERSION, defaultName, sources, null, null, null, null));
        }
        choices.sort((a, b) -> IgnoreCase.ORDER.compare(a.outputName, b.outputName));
        String suggested = CombinedModStateStore.suggestNewOutputName(p, defaultName);
        edt(() -> {
            settingSelection = true;
            try {
                DefaultComboBoxModel<CombinedModState> model = new DefaultComboBoxModel<>();
                for (CombinedModState c : choices) model.addElement(c);
                existingMergeBox.setModel(model);
                existingMergeBox.setSelectedIndex(choices.isEmpty() ? -1 : 0);
                existing = (CombinedModState) existingMergeBox.getSelectedItem();
                outputNameBox.setText(suggested);
            } finally {
                settingSelection = false;
            }
        });
        tryGeneratePreview(token);
        checkExisting(token);
    }

    private void checkExisting(Cancel token) {
        CombinedModState state = edt(() -> {
            existing = (CombinedModState) existingMergeBox.getSelectedItem();
            changedMods = Collections.emptyList();
            if (existing == null || paths == null || order.size() < 2) {
                if (previewError == null && order.size() >= 2) status = "Ready to create a new combination.";
                return null;
            }
            return existing;
        });
        if (state == null) return;
        WarnoPaths p = edt(() -> paths);
        List<ModDescriptor> mods = edt(() -> new ArrayList<>(order));
        AtomicInteger sourcePct = new AtomicInteger(), outputPct = new AtomicInteger();
        Consumer<CombineProgress> ui = progressReporter();
        Runnable combined = () -> ui.accept(new CombineProgress((sourcePct.get() + outputPct.get()) / 2, "Checking '" + state.outputName + "'"));
        // Source fingerprints and the output check run side by side.
        List<SourceModFingerprint>[] current = new List[1];
        String[] report = new String[1];
        com.warnomodmaker.combine.Parallel.forEach(2, 2, i -> {
            if (i == 0) current[0] = fingerprints.compute(mods, x -> { sourcePct.set(x.percent); combined.run(); }, token);
            else report[0] = health.check(p, state, x -> { outputPct.set(x.percent); combined.run(); }, token);
        });
        List<String> changed = new ArrayList<>();
        for (SourceModFingerprint f : current[0]) {
            if (state.sources().stream().noneMatch(stored -> CombinedModStateStore.fingerprintMatches(stored, f))) changed.add(f.name);
        }
        boolean reordered = state.sources().size() != mods.size();
        for (int i = 0; !reordered && i < mods.size(); i++)
            reordered = !CombinedModStateStore.sameSource(state.sources().get(i).rootPath, mods.get(i).rootPath.toString());
        boolean differs = reordered;
        edt(() -> {
            changedMods = changed;
            status = (changed.isEmpty() ? "Inputs match the last merge. " : "Changed: " + String.join(", ", changed) + ". ")
                + (differs ? "Priority order differs from the last build. " : "") + report[0];
        });
        log("[CHECK] '" + state.outputName + "': " + status);
    }

    private void tryGeneratePreview(Cancel token) {
        WarnoPaths p = edt(() -> order.size() < 2 ? null : paths);
        if (p == null) return;
        List<ModDescriptor> mods = edt(() -> new ArrayList<>(order));
        String candidate = edt(() -> {
            String name = outputNameBox.getText().trim();
            if (!MergePlanner.isValidOutputName(name))
                name = existing != null ? existing.outputName : CombinedModStateStore.suggestNewOutputName(paths, defaultOutputName());
            return name;
        });
        try {
            progressReporter().accept(new CombineProgress(50, "Delta merging databases for preview"));
            MergePreview plan = planner.createPreview(p, mods, candidate, true, token);
            edt(() -> {
                previewError = null;
                displayPreview(plan, false);
            });
        } catch (CombineException e) {
            edt(() -> {
                previewError = e.getMessage();
                clearPreview();
                status = e.getMessage();
            });
            log("[WARN] " + e.getMessage());
        }
    }

    private CombineRequest request(boolean rebuild, Cancel token) {
        WarnoPaths p = edt(() -> paths);
        if (p == null || edt(() -> order.size()) < 2) throw new CombineException("Add at least two mods to combine.");
        String folder = edt(() -> warnoPathBox.getText().trim());
        if (!FileOps.samePath(Paths.get(folder), p.warnoRoot)) throw new CombineException("The WARNO folder changed. Refresh mods first.");
        String name = edt(() -> rebuild ? (existing != null ? existing.outputName : null) : outputNameBox.getText().trim());
        if (name == null) throw new CombineException("Select an existing merge to rebuild.");
        List<ModDescriptor> selected = edt(() -> new ArrayList<>(order));
        List<ModDescriptor> now = scanner.scan(p, token);
        List<ModDescriptor> fresh = new ArrayList<>();
        for (ModDescriptor mod : selected) {
            ModDescriptor match = now.stream().filter(m -> FileOps.samePath(m.rootPath, mod.rootPath)).findFirst()
                .orElseThrow(() -> new CombineException(mod.name + " is no longer installed. Refresh mods."));
            fresh.add(match);
        }
        MergePreview plan = planner.createPreview(p, fresh, name, rebuild, token);
        return new CombineRequest(p, fresh, name, plan);
    }

    private void build(boolean rebuild, Cancel token) {
        CombineRequest request = request(rebuild, token);
        edt(() -> {
            previewError = null;
            displayPreview(request.preview, false);
        });
        List<String> changed = edt(() -> changedMods);
        String changedLine = rebuild && !changed.isEmpty() ? "Updated source mod(s): " + String.join(", ", changed) + "\n\n" : "";
        if (!confirm((rebuild ? "Rebuild" : "Create") + " '" + request.outputName + "'?\n\n" + changedLine + "Priority: " + request.preview.priorityLabel() + "\n\n"
                + request.preview.summary() + "." + (request.preview.provisional ? "\nEditable input will be regenerated; the final report will show the exact compiled conflicts." : ""),
            "Confirm merge", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE)) return;
        Consumer<CombineProgress> progress = progressReporter();
        CombineResult result = rebuild ? service.rebuild(request, this::log, progress, token) : service.combine(request, this::log, progress, token);
        String suggested = CombinedModStateStore.suggestNewOutputName(request.paths, request.mods.stream().map(m -> m.name).collect(Collectors.joining(" + ")));
        edt(() -> {
            existing = result.state;
            settingSelection = true;
            try {
                List<CombinedModState> choices = new ArrayList<>();
                for (int i = 0; i < existingMergeBox.getItemCount(); i++) {
                    CombinedModState s = existingMergeBox.getItemAt(i);
                    if (!s.outputName.equalsIgnoreCase(result.state.outputName)) choices.add(s);
                }
                choices.add(result.state);
                choices.sort((a, b) -> IgnoreCase.ORDER.compare(a.outputName, b.outputName));
                DefaultComboBoxModel<CombinedModState> model = new DefaultComboBoxModel<>();
                for (CombinedModState c : choices) model.addElement(c);
                existingMergeBox.setModel(model);
                existingMergeBox.setSelectedItem(result.state);
                outputNameBox.setText(suggested);
            } finally {
                settingSelection = false;
            }
            changedMods = Collections.emptyList();
            status = "Combined mod verified. The final merge report is available to inspect or export.";
            displayPreview(result.plan, true);
        });
        log("[DONE] " + result.outputSourcePath);
        edt(() -> JOptionPane.showMessageDialog(this, "Combined mod " + (rebuild ? "rebuilt" : "created") + " successfully.\n\n" + result.outputSourcePath,
            "Combine mods", JOptionPane.INFORMATION_MESSAGE));
    }

    // ───────────── Operations ─────────────

    private interface Operation {
        void run(Cancel token) throws Exception;
    }

    /** Runs one operation at a time on the worker thread, with the busy state, cancellation and error reporting. */
    private void runUi(String stage, Operation action) {
        if (busy) return;
        Cancel token = new Cancel();
        operation = token;
        setBusy(true, stage);
        worker.submit(() -> {
            try {
                action.run(token);
            } catch (CancellationException e) {
                status = "Operation cancelled. Any previous output was restored unless a recovery error was reported.";
                log("[CANCEL] " + status);
            } catch (Throwable e) {
                Throwable cause = e instanceof UncheckedIOException ? e.getCause() : e;
                status = "Operation failed: " + message(cause);
                log("[ERROR] " + message(cause));
                if (!(cause instanceof CombineException)) cause.printStackTrace();
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, message(cause), "Combine mods", JOptionPane.ERROR_MESSAGE));
            } finally {
                operation = null;
                SwingUtilities.invokeLater(() -> setBusy(false, null));
            }
        });
    }

    private static String message(Throwable e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    public boolean isBusy() {
        return busy;
    }

    /** Asks the running operation to stop; rebuild recovery still finishes before the panel is idle. */
    public void requestCancel() {
        Cancel current = operation;
        if (current != null) current.cancel();
        cancelButton.setEnabled(false);
        progressText.setText("Cancelling, and waiting for WARNO and recovery to finish…");
    }

    /** Stops background work when the window closes. */
    public void shutdown() {
        Cancel check = updateCheck;
        if (check != null) check.cancel();
        worker.shutdownNow();
        checker.shutdownNow();
    }

    /** Runs on the event thread and waits for it, from any thread. */
    private <T> T edt(Supplier<T> action) {
        if (SwingUtilities.isEventDispatchThread()) return action.get();
        Object[] result = new Object[1];
        RuntimeException[] error = new RuntimeException[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    result[0] = action.get();
                } catch (RuntimeException e) {
                    error[0] = e;
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException();
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new IllegalStateException(e.getCause());
        }
        if (error[0] != null) throw error[0];
        @SuppressWarnings("unchecked") T value = (T) result[0];
        return value;
    }

    private void edt(Runnable action) {
        edt(() -> {
            action.run();
            return null;
        });
    }

    private boolean confirm(String message, String title, int options, int type) {
        return edt(() -> {
            int answer = JOptionPane.showConfirmDialog(this, message, title, options, type);
            return answer == JOptionPane.OK_OPTION || answer == JOptionPane.YES_OPTION;
        });
    }

    /** Progress for the busy bar, always applied on the event thread. */
    private Consumer<CombineProgress> progressReporter() {
        return p -> SwingUtilities.invokeLater(() -> {
            Cancel current = operation;
            if (!busy || current != null && current.isCancelled()) return;
            busyBar.setValue(p.percent);
            progressText.setText(p.stage + " · " + p.percent + "%");
        });
    }

    private void setBusy(boolean value, String stage) {
        busy = value;
        progressPanel.setVisible(value);
        cancelButton.setVisible(value);
        cancelButton.setEnabled(value);
        if (value) {
            busyBar.setValue(0);
            progressText.setText(stage);
        }
        conflictsOnlyBox.setEnabled(!value);
        searchBox.setEnabled(!value);
        for (JComponent c : new JComponent[]{browseButton, refreshButton, warnoPathBox, availableModBox, mergeOrderList, cleanLeftoversButton, checkUpdatesButton})
            c.setEnabled(!value);
        existingMergeBox.setEnabled(!value && existingMergeBox.getItemCount() > 0);
        updateOrderButtons();
        updateActionStates();
    }

    private void updateActionStates() {
        boolean selected = paths != null && order.size() >= 2;
        String name = outputNameBox.getText().trim();
        boolean valid = MergePlanner.isValidOutputName(name);
        boolean exists = valid && paths != null && CombinedModStateStore.outputExists(paths, name);
        boolean compatible = previewError == null;

        combineButton.setEnabled(!busy && selected && valid && !exists && compatible);
        updateButton.setEnabled(!busy && selected && existing != null && compatible);
        deleteMergeButton.setEnabled(!busy && existingMergeBox.getSelectedItem() != null);
        updateAllButton.setEnabled(!busy && updates.stream().anyMatch(CombinedModUpdater.Update::canUpdate));
        updateWithoutOutdatedButton.setEnabled(!busy && updates.stream().anyMatch(CombinedModUpdater.Update::canUpdateWithoutOutdated));
        updateButton.setText(!changedMods.isEmpty() ? "Update and rebuild" : "Rebuild existing");
        outputNameBox.setEnabled(!busy);
        exportButton.setEnabled(!busy && preview != null);

        combineButton.setToolTipText(!selected ? "Add at least two mods to combine."
            : !compatible ? previewError
            : !valid ? "Enter a valid Windows folder name."
            : exists ? "An output named '" + name + "' already exists."
            : "Create a new combined mod named '" + name + "'.");
        updateButton.setToolTipText(existing == null ? "No combined mod exists for these mods yet. Create one first."
            : !compatible ? previewError
            : !changedMods.isEmpty() ? "Changed: " + String.join(", ", changedMods)
            : "Rebuild and verify '" + existing.outputName + "' in place using the current priority order.");
        statusText.setText("<html><body style='width:320px'>" + escape(status) + "</body></html>");
        statusText.setVisible(!busy);
    }

    // ───────────── Plan ─────────────

    private void displayPreview(MergePreview plan, boolean forceLog) {
        preview = plan;
        applyPreviewFilter();
        String key = plan.mods.stream().map(m -> m.rootPath.toString()).collect(Collectors.joining("|")) + "|" + plan.decisions.size() + "|" + plan.conflicts.size() + "|" + plan.provisional;
        if (forceLog || !key.equalsIgnoreCase(lastLoggedPreviewKey)) {
            lastLoggedPreviewKey = key;
            log("[PLAN] " + plan.mods.stream().map(m -> "'" + m.name + "'").collect(Collectors.joining(" > ")) + ": " + plan.summary() + ".");
            for (String warning : plan.warnings) log("[NOTE] " + warning);
        }
        updateActionStates();
    }

    private void clearPreview() {
        preview = null;
        planModel.setRows(Collections.emptyList());
        summaryText.setText("Every file the combined mod will contain, and how each one is produced.");
        for (JLabel stat : new JLabel[]{statFiles, statMerged, statConflicts, statOverrides, statSingle, statIdentical}) stat.setText("–");
        emptyPlanTitle.setText(previewError == null ? "No plan yet" : "This combination can't be built");
        emptyPlanText.setText(previewError != null ? previewError
            : "Add two or more mods on the left. The plan shows every file and every value the mods disagree on before anything is built.");
        planCards.show(planHost, "empty");
    }

    private void applyPreviewFilter() {
        if (preview == null) return;
        statFiles.setText(String.format("%,d", preview.decisions.size()));
        statMerged.setText(String.format("%,d", preview.mergedCount));
        statConflicts.setText(String.format("%,d", preview.valueConflictCount()));
        statOverrides.setText(String.format("%,d", preview.overrideCount));
        statSingle.setText(String.format("%,d", preview.singleCount));
        statIdentical.setText(String.format("%,d", preview.identicalCount));
        String query = searchBox.getText().trim().toLowerCase();
        List<MergeDecision> all = new ArrayList<>(preview.decisions);
        all.addAll(preview.conflicts);
        boolean conflictsOnly = conflictsOnlyBox.isSelected();
        List<MergeDecision> rows = all.stream().filter(d -> (!conflictsOnly || d.isCollision() && !d.identical)
            && (query.isEmpty() || d.relativePath.toLowerCase().contains(query) || d.actionLabel().toLowerCase().contains(query)
            || d.winner.toLowerCase().contains(query) || d.detail.toLowerCase().contains(query))).collect(Collectors.toList());
        planModel.setRows(rows);
        summaryText.setText(String.format("%s · showing %,d of %,d rows%s", preview.priorityLabel(), rows.size(), all.size(),
            preview.provisional ? " · provisional until editable mods are regenerated" : ""));
        emptyPlanTitle.setText("Nothing matches");
        emptyPlanText.setText("No row matches the search or filter.");
        planCards.show(planHost, rows.isEmpty() ? "empty" : "grid");
    }

    private void export() {
        if (preview == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export merge report");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON report", "json"));
        chooser.setSelectedFile(new File("merge-report.json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File target = chooser.getSelectedFile().getName().toLowerCase().endsWith(".json") ? chooser.getSelectedFile()
            : new File(chooser.getSelectedFile().getPath() + ".json");
        MergePreview plan = preview;
        String logText = logBox.getText();
        runUi("Exporting report", token -> {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("TotalFiles", plan.decisions.size());
            summary.put("MergedCount", plan.mergedCount);
            summary.put("ValueConflictCount", plan.valueConflictCount());
            summary.put("OverrideCount", plan.overrideCount);
            summary.put("SingleCount", plan.singleCount);
            summary.put("IdenticalCount", plan.identicalCount);
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("OutputName", plan.outputName);
            report.put("PriorityOrder", plan.mods.stream().map(m -> m.name).collect(Collectors.toList()));
            report.put("Primary", plan.primary().name);
            report.put("Provisional", plan.provisional);
            report.put("Summary", summary);
            report.put("Warnings", plan.warnings);
            report.put("Decisions", plan.decisions.stream().map(CombinePanel::row).collect(Collectors.toList()));
            report.put("ValueConflicts", plan.conflicts.stream().map(CombinePanel::row).collect(Collectors.toList()));
            report.put("Log", logText);
            try {
                Files.write(target.toPath(), Json.write(report).getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            log("[EXPORT] Report exported to " + target);
        });
    }

    private static Map<String, Object> row(MergeDecision d) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("RelativePath", d.relativePath);
        row.put("Kind", d.kind.name());
        row.put("Action", d.actionLabel());
        row.put("Winner", d.winner);
        row.put("Detail", d.detail);
        row.put("Identical", d.identical);
        row.put("IsCollision", d.isCollision());
        return row;
    }

    // ───────────── Small helpers ─────────────

    /** Shows the folder with its end in view: the WARNO folder name matters more than the drive. */
    private void showWarnoPath(String path) {
        warnoPathBox.setText(path);
        warnoPathBox.setToolTipText(path);
        warnoPathBox.setCaretPosition(path.length());
    }

    private void log(String message) {
        String line = "[" + LocalTime.now().format(CLOCK) + "] " + message + "\n";
        SwingUtilities.invokeLater(() -> {
            logBox.append(line);
            logBox.setCaretPosition(logBox.getDocument().getLength());
        });
    }

    private static boolean isUlti(ModDescriptor mod) {
        return mod.name.toLowerCase().contains("ultiai") || mod.rootPath.getFileName().toString().toLowerCase().contains("ultiai");
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ───────────── Renderers and models ─────────────

    /** The merge order: position, name, kind and a PRIMARY badge on the top mod. */
    private final class OrderRenderer implements ListCellRenderer<ModDescriptor> {
        private final RoundedPanel cell = new RoundedPanel(new BorderLayout(10, 0), WarnoTheme.RAISED, WarnoTheme.BORDER, 8);
        private final JLabel position = new JLabel("", SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(WarnoTheme.BACKGROUND);
                g2.fillOval(0, (getHeight() - 24) / 2, 24, 24);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        private final JLabel name = new JLabel();
        private final JLabel kind = new JLabel();
        private final RoundedPanel primary = new RoundedPanel(new BorderLayout(), WarnoTheme.ACCENT_SOFT, WarnoTheme.ACCENT_SOFT_BORDER, 5);
        private final JPanel wrapper = new JPanel(new BorderLayout());

        OrderRenderer() {
            cell.setBorder(WarnoTheme.pad(8, 10, 8, 10));
            position.setPreferredSize(new Dimension(24, 24));
            position.setFont(WarnoTheme.semibold(11));
            position.setForeground(WarnoTheme.MUTED);
            name.setFont(WarnoTheme.semibold(13));
            name.setForeground(WarnoTheme.TEXT);
            kind.setFont(WarnoTheme.ui(11));
            kind.setForeground(WarnoTheme.MUTED);
            JPanel text = column();
            text.add(name);
            text.add(kind);
            JLabel badge = new JLabel("PRIMARY");
            badge.setFont(WarnoTheme.semibold(10));
            badge.setForeground(WarnoTheme.ACCENT);
            primary.setBorder(WarnoTheme.pad(2, 7, 2, 7));
            primary.add(badge);
            JPanel badgeHost = new JPanel(new GridBagLayout());
            badgeHost.setOpaque(false);
            badgeHost.add(primary);
            cell.add(position, BorderLayout.WEST);
            cell.add(text, BorderLayout.CENTER);
            cell.add(badgeHost, BorderLayout.EAST);
            wrapper.setOpaque(false);
            wrapper.setBorder(WarnoTheme.pad(0, 0, 4, 0));
            wrapper.add(cell);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ModDescriptor> list, ModDescriptor value, int index, boolean selected, boolean focus) {
            position.setText(Integer.toString(index + 1));
            name.setText(value.name);
            kind.setText(value.kindLabel() + " · " + value.modGenLabel());
            primary.setVisible(index == 0);
            cell.setColors(selected ? WarnoTheme.SELECTION : WarnoTheme.RAISED, selected ? WarnoTheme.ACCENT : WarnoTheme.BORDER);
            return wrapper;
        }
    }

    /** Two lines per mod in the picker: its name, and where it comes from. */
    private static final class ModComboRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof ModDescriptor) {
                ModDescriptor mod = (ModDescriptor) value;
                setText("<html><span style='color:#E8EAEF'>" + escape(mod.name) + "</span><br><span style='font-size:9px;color:#9AA3B2'>"
                    + escape(mod.kindLabel() + " · " + mod.modGenLabel()) + "</span></html>");
            } else if (value == null) {
                setText("No more installed mods");
            }
            setBorder(WarnoTheme.pad(4, 6, 4, 6));
            return this;
        }
    }

    private static final class PaddedRenderer extends DefaultTableCellRenderer {
        private final Font font;
        private final Color color;

        PaddedRenderer(Font font, Color color) {
            this.font = font;
            this.color = color;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, false, row, column);
            setFont(font);
            setForeground(color);
            setBorder(WarnoTheme.pad(0, 10, 0, 10));
            setToolTipText(value == null ? null : value.toString());
            return this;
        }
    }

    /** The colour-coded decision pill. */
    private static final class BadgeRenderer extends JComponent implements javax.swing.table.TableCellRenderer {
        private MergeDecision decision;
        private boolean selected;

        BadgeRenderer() {
            setFont(WarnoTheme.semibold(11.5f));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean focus, int row, int column) {
            decision = (MergeDecision) value;
            selected = isSelected;
            setBackground(isSelected ? WarnoTheme.SELECTION : row % 2 == 1 ? WarnoTheme.ALT_ROW : WarnoTheme.PANEL);
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());
            if (decision != null) {
                WarnoTheme.Badge badge = WarnoTheme.badge(decision.actionKey());
                String text = decision.actionLabel();
                g2.setFont(getFont());
                FontMetrics metrics = g2.getFontMetrics();
                int width = 8 + 6 + 6 + metrics.stringWidth(text) + 8, height = 22;
                int x = 10, y = (getHeight() - height) / 2;
                g2.setColor(badge.background);
                g2.fillRoundRect(x, y, width, height, 10, 10);
                g2.setColor(badge.border);
                g2.drawRoundRect(x, y, width - 1, height - 1, 10, 10);
                g2.setColor(badge.dot);
                g2.fillOval(x + 8, y + (height - 6) / 2, 6, 6);
                g2.setColor(badge.text);
                g2.drawString(text, x + 20, y + (height - metrics.getHeight()) / 2 + metrics.getAscent());
            }
            g2.dispose();
        }
    }

    private static final class PlanModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"FILE", "DECISION", "FROM", "WHY"};
        private List<MergeDecision> rows = Collections.emptyList();

        void setRows(List<MergeDecision> newRows) {
            rows = newRows;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 1 ? MergeDecision.class : String.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            MergeDecision d = rows.get(row);
            switch (column) {
                case 0: return d.relativePath;
                case 1: return d;
                case 2: return d.winner;
                default: return d.detail;
            }
        }
    }

    /** A sidebar column that tracks the viewport's width, so text wraps instead of scrolling sideways. */
    private static final class ScrollablePanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return visibleRect.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
