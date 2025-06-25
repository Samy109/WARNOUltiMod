package test.java.com.warnomodmaker;

import com.warnomodmaker.gui.theme.WarnoTheme;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.util.*;

/**
 * Comprehensive UI for E2E Test execution and results display
 */
public class TestUI extends JFrame implements TestRunner.TestProgressListener {
    
    private JTree testTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    
    private JTextArea logArea;
    private JTextPane logPane;
    private JScrollPane logScrollPane;
    
    private JProgressBar overallProgress;
    private JLabel statusLabel;
    private JLabel currentTestLabel;
    
    private JPanel summaryPanel;
    private JLabel totalTestsLabel;
    private JLabel passedTestsLabel;
    private JLabel failedTestsLabel;
    private JLabel totalTimeLabel;
    private JLabel avgTimeLabel;
    private JLabel assertionsLabel;
    
    private JButton runAllButton;
    private JButton runSelectedButton;
    private JMenuItem exportMenuItem;
    private JButton clearLogButton;

    private TestRunner testRunner;
    private ComprehensiveE2ETest testInstance;
    private TestRunner.TestResults lastResults;
    private int totalTests = 0;
    private int completedTests = 0;

    private DecimalFormat timeFormat = new DecimalFormat("#,##0.0");
    private DecimalFormat numberFormat = new DecimalFormat("#,##0");

    private DebugConsoleWindow debugConsole;
    
    public TestUI(ComprehensiveE2ETest testInstance) {
        super("WARNO Mod Maker - E2E Test Suite");
        this.testInstance = testInstance;
        this.testRunner = new TestRunner();
        this.testRunner.addProgressListener(this);

        // Initialize debug console
        this.debugConsole = new DebugConsoleWindow();

        // Modern UI tweaks
        UIManager.put("ProgressBar.selectionForeground", Color.BLACK);
        UIManager.put("ProgressBar.selectionBackground", Color.BLACK);

        initializeUI();
        setupTests();
        setupEventHandlers();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);

        // Ensure debug console is properly disposed when main window closes
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (debugConsole != null) {
                    debugConsole.dispose();
                }
                System.exit(0);
            }
        });
    }
    
    private void initializeUI() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(45, 45, 48)); // Modern dark background

        // Create menu bar
        setJMenuBar(createMenuBar());

        // Create main panels
        createTestTreePanel();
        createLogPanel();
        createControlPanel();
        createSummaryPanel();

        // Layout main components with modern styling
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setLeftComponent(createLeftPanel());
        mainSplit.setRightComponent(createRightPanel());
        mainSplit.setDividerLocation(400);
        mainSplit.setResizeWeight(0.3);
        mainSplit.setBackground(new Color(45, 45, 48));
        mainSplit.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8)); // Modern padding
        mainSplit.setDividerSize(2); // Thinner, modern divider

        add(mainSplit, BorderLayout.CENTER);
        add(createControlPanel(), BorderLayout.SOUTH);
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBackground(new Color(37, 37, 38)); // Modern darker menu bar
        menuBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 60, 60))); // Subtle bottom border

        // File menu
        JMenu fileMenu = new JMenu("File");
        fileMenu.setForeground(Color.WHITE);

        exportMenuItem = new JMenuItem("Export Results...");
        exportMenuItem.addActionListener(e -> exportResults());
        exportMenuItem.setEnabled(false);
        fileMenu.add(exportMenuItem);

        fileMenu.addSeparator();

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);

        // View menu
        JMenu viewMenu = new JMenu("View");
        viewMenu.setForeground(Color.WHITE);

        JMenuItem clearLogItem = new JMenuItem("Clear Log");
        clearLogItem.addActionListener(e -> clearLog());
        viewMenu.add(clearLogItem);

        JMenuItem expandAllItem = new JMenuItem("Expand All");
        expandAllItem.addActionListener(e -> expandAllTreeNodes());
        viewMenu.add(expandAllItem);

        JMenuItem collapseAllItem = new JMenuItem("Collapse All");
        collapseAllItem.addActionListener(e -> collapseAllTreeNodes());
        viewMenu.add(collapseAllItem);

        viewMenu.addSeparator();

        JMenuItem debugConsoleItem = new JMenuItem("Debug Console");
        debugConsoleItem.addActionListener(e -> showDebugConsole());
        viewMenu.add(debugConsoleItem);

        // Help menu
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setForeground(Color.WHITE);

        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> showAboutDialog());
        helpMenu.add(aboutItem);

        menuBar.add(fileMenu);
        menuBar.add(viewMenu);
        menuBar.add(helpMenu);

        // Store reference to export menu item for enabling/disabling
        // exportButton will be set in createControlPanel()

        return menuBar;
    }

    private void expandAllTreeNodes() {
        for (int i = 0; i < testTree.getRowCount(); i++) {
            testTree.expandRow(i);
        }
    }

    private void collapseAllTreeNodes() {
        for (int i = testTree.getRowCount() - 1; i >= 0; i--) {
            testTree.collapseRow(i);
        }
    }

    private void showAboutDialog() {
        String message = "WARNO Mod Maker E2E Test Suite\n\n" +
                        "A comprehensive testing framework for the WARNO Mod Maker application.\n" +
                        "This tool validates all core functionality including:\n" +
                        "• File parsing and writing\n" +
                        "• Model integrity\n" +
                        "• Modification operations\n" +
                        "• New features\n" +
                        "• Stress testing\n\n" +
                        "Built with enhanced UI for better test visibility and debugging.";

        JOptionPane.showMessageDialog(this, message, "About E2E Test Suite", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private JPanel createLeftPanel() {
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 1, 1, 0, new Color(60, 60, 60)), // Modern border
            BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(8, 8, 8, 8),
                "Test Categories",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font(Font.SANS_SERIF, Font.BOLD, 12),
                new Color(220, 220, 220)))); // Softer white
        leftPanel.setBackground(new Color(50, 50, 53)); // Modern panel background
        
        // Test tree
        rootNode = new DefaultMutableTreeNode("E2E Test Suite");
        treeModel = new DefaultTreeModel(rootNode);
        testTree = new JTree(treeModel);
        testTree.setRootVisible(true);
        testTree.setShowsRootHandles(true);
        testTree.setBackground(WarnoTheme.TACTICAL_GREEN_LIGHT);
        testTree.setForeground(Color.WHITE);

        // Disable multiple selection to avoid weird highlighting
        testTree.getSelectionModel().setSelectionMode(javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION);

        // Set UI properties for better tree appearance
        testTree.putClientProperty("JTree.lineStyle", "None");
        testTree.setOpaque(true);

        // Set row height for better appearance
        testTree.setRowHeight(20);

        // Custom cell renderer for better appearance
        testTree.setCellRenderer(new TestTreeCellRenderer());

        JScrollPane treeScrollPane = new JScrollPane(testTree);
        treeScrollPane.setPreferredSize(new Dimension(380, 0));
        treeScrollPane.setBorder(BorderFactory.createEmptyBorder()); // Clean, borderless
        treeScrollPane.getViewport().setBackground(WarnoTheme.TACTICAL_GREEN_LIGHT);
        leftPanel.add(treeScrollPane, BorderLayout.CENTER);
        
        // Summary panel
        leftPanel.add(createSummaryPanel(), BorderLayout.SOUTH);
        
        return leftPanel;
    }
    
    private JPanel createRightPanel() {
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 1, 1, new Color(60, 60, 60)), // Modern border
            BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(8, 8, 8, 8),
                "Test Execution Log",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font(Font.SANS_SERIF, Font.BOLD, 12),
                new Color(220, 220, 220)))); // Softer white
        rightPanel.setBackground(new Color(50, 50, 53)); // Modern panel background
        
        // Log area - use JTextPane for better color support
        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setBackground(new Color(250, 250, 250)); // Clean, modern white
        logPane.setForeground(new Color(40, 40, 40)); // Softer black text
        logPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logPane.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8)); // Internal padding
        
        logScrollPane = new JScrollPane(logPane);
        logScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        rightPanel.add(logScrollPane, BorderLayout.CENTER);
        
        // Status panel
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBackground(new Color(50, 50, 53));
        statusPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(60, 60, 60)), // Top border only
            BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(8, 8, 8, 8),
                "Current Status",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font(Font.SANS_SERIF, Font.BOLD, 12),
                new Color(220, 220, 220))));
        
        currentTestLabel = new JLabel("Ready to run tests") {
            @Override
            public void setForeground(Color fg) {
                // Always force white color
                super.setForeground(Color.WHITE);
            }
        };
        currentTestLabel.setForeground(Color.WHITE);
        currentTestLabel.setFont(currentTestLabel.getFont().deriveFont(Font.BOLD));
        currentTestLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        overallProgress = new JProgressBar();
        overallProgress.setStringPainted(true);
        overallProgress.setString("Ready");
        overallProgress.setBackground(WarnoTheme.TACTICAL_GREEN_LIGHT);
        overallProgress.setForeground(new Color(0, 128, 0)); // Dark green

        // Set progress bar string color using UIManager
        UIManager.put("ProgressBar.selectionForeground", Color.BLACK);
        UIManager.put("ProgressBar.selectionBackground", Color.BLACK);

        // Ensure labels stay white on dark backgrounds
        UIManager.put("Label.foreground", null); // Don't override label colors globally
        
        statusPanel.add(currentTestLabel, BorderLayout.NORTH);
        statusPanel.add(overallProgress, BorderLayout.CENTER);
        
        rightPanel.add(statusPanel, BorderLayout.SOUTH);
        
        return rightPanel;
    }
    
    private JPanel createSummaryPanel() {
        summaryPanel = new JPanel(new GridLayout(3, 2, 8, 4)); // Better spacing
        summaryPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(60, 60, 60)), // Top border
            BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(12, 12, 12, 12),
                "Test Summary",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font(Font.SANS_SERIF, Font.BOLD, 12),
                new Color(220, 220, 220))));
        summaryPanel.setBackground(new Color(45, 45, 48)); // Match main background
        
        totalTestsLabel = createSummaryLabel("Total Tests: 0");
        passedTestsLabel = createSummaryLabel("Passed: 0");
        failedTestsLabel = createSummaryLabel("Failed: 0");
        totalTimeLabel = createSummaryLabel("Total Time: 0ms");
        avgTimeLabel = createSummaryLabel("Avg Time: 0ms");
        assertionsLabel = createSummaryLabel("Assertions: 0");
        
        summaryPanel.add(totalTestsLabel);
        summaryPanel.add(passedTestsLabel);
        summaryPanel.add(failedTestsLabel);
        summaryPanel.add(totalTimeLabel);
        summaryPanel.add(avgTimeLabel);
        summaryPanel.add(assertionsLabel);
        
        return summaryPanel;
    }
    
    private JLabel createSummaryLabel(String text) {
        JLabel label = new JLabel(text) {
            @Override
            public void setForeground(Color fg) {
                // Always force white color
                super.setForeground(Color.WHITE);
            }
        };
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        return label;
    }
    
    private JPanel createControlPanel() {
        JPanel controlPanel = new JPanel(new FlowLayout());
        controlPanel.setBackground(WarnoTheme.TACTICAL_GREEN_DARK);
        controlPanel.setBorder(BorderFactory.createRaisedBevelBorder());
        
        runAllButton = new JButton("Run All Tests");
        runAllButton.setBackground(new Color(0, 120, 215)); // Modern blue
        runAllButton.setForeground(Color.WHITE);
        runAllButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        runAllButton.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16)); // Modern padding
        runAllButton.setFocusPainted(false); // Remove focus border
        
        runSelectedButton = new JButton("Run Selected");
        runSelectedButton.setBackground(new Color(16, 110, 190)); // Darker blue
        runSelectedButton.setForeground(Color.WHITE);
        runSelectedButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        runSelectedButton.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        runSelectedButton.setFocusPainted(false);
        runSelectedButton.setEnabled(false);
        


        clearLogButton = new JButton("Clear Log");
        clearLogButton.setBackground(new Color(96, 96, 96)); // Modern gray
        clearLogButton.setForeground(Color.WHITE);
        clearLogButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        clearLogButton.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        clearLogButton.setFocusPainted(false);

        controlPanel.add(runAllButton);
        controlPanel.add(Box.createHorizontalStrut(12)); // Modern spacing
        controlPanel.add(runSelectedButton);
        controlPanel.add(Box.createHorizontalStrut(24)); // Larger gap for grouping
        controlPanel.add(clearLogButton);
        
        return controlPanel;
    }
    
    private void createTestTreePanel() {
        // This will be populated by setupTests()
    }
    
    private void createLogPanel() {
        // Already created in createRightPanel()
    }
    
    private void setupTests() {
        // Clear existing tree
        rootNode.removeAllChildren();
        
        // Create category nodes
        Map<TestRunner.TestCategory, DefaultMutableTreeNode> categoryNodes = new java.util.HashMap<>();
        
        for (TestRunner.TestCategory category : TestRunner.TestCategory.values()) {
            DefaultMutableTreeNode categoryNode = new DefaultMutableTreeNode(category.getDisplayName());
            categoryNodes.put(category, categoryNode);
            rootNode.add(categoryNode);
        }
        
        // Add tests to runner and tree (this will be populated by the test instance)
        populateTestsFromInstance();
        
        treeModel.reload();
        
        // Expand all nodes
        for (int i = 0; i < testTree.getRowCount(); i++) {
            testTree.expandRow(i);
        }
    }
    
    private void populateTestsFromInstance() {
        // Create a temporary runner to get the test structure
        TestRunner tempRunner = new TestRunner();
        testInstance.setupTestRunner(tempRunner);

        // Group tests by category
        Map<TestRunner.TestCategory, DefaultMutableTreeNode> categoryNodes = new HashMap<>();

        // Create category nodes
        for (TestRunner.TestCategory category : TestRunner.TestCategory.values()) {
            DefaultMutableTreeNode categoryNode = new DefaultMutableTreeNode(category.getDisplayName());
            categoryNodes.put(category, categoryNode);
        }

        // Add test nodes to categories
        for (TestRunner.TestCase testCase : tempRunner.getTestCases()) {
            DefaultMutableTreeNode categoryNode = categoryNodes.get(testCase.category);
            if (categoryNode != null) {
                DefaultMutableTreeNode testNode = new DefaultMutableTreeNode(testCase.name);
                categoryNode.add(testNode);
            }
        }

        // Add non-empty category nodes to root
        for (TestRunner.TestCategory category : TestRunner.TestCategory.values()) {
            DefaultMutableTreeNode categoryNode = categoryNodes.get(category);
            if (categoryNode.getChildCount() > 0) {
                rootNode.add(categoryNode);
            }
        }

        logMessage("Test structure loaded: " + rootNode.getChildCount() + " categories", new Color(0, 102, 153)); // Dark blue
    }
    
    private void setupEventHandlers() {
        runAllButton.addActionListener(e -> runAllTests());
        runSelectedButton.addActionListener(e -> runSelectedTests());
        clearLogButton.addActionListener(e -> clearLog());
        
        testTree.addTreeSelectionListener(e -> {
            TreePath[] paths = testTree.getSelectionPaths();
            boolean hasSelection = paths != null && paths.length > 0;
            runSelectedButton.setEnabled(hasSelection);

            // Clear any weird selection artifacts
            if (hasSelection) {
                testTree.repaint();
            }
        });
    }
    
    private void runAllTests() {
        SwingUtilities.invokeLater(() -> {
            runAllButton.setEnabled(false);
            clearLog();
            TestAssert.resetCounters();

            // Count total tests
            TestRunner tempRunner = new TestRunner();
            testInstance.setupTestRunner(tempRunner);
            totalTests = tempRunner.getTestCases().size();
            completedTests = 0;

            overallProgress.setMaximum(totalTests);
            overallProgress.setValue(0);
            overallProgress.setString("0 / " + totalTests);

            logMessage("=== WARNO Mod Maker E2E Test Suite ===", new Color(0, 102, 153)); // Dark blue
            logMessage("Starting comprehensive test execution...", Color.BLACK);
            logMessage("Total tests to run: " + totalTests + "\n", Color.BLACK);

            // Run tests in background thread
            new Thread(() -> {
                try {
                    testInstance.runCompleteTestWithUI(testRunner);
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        logMessage("FATAL ERROR: " + e.getMessage(), WarnoTheme.ERROR_RED);
                        e.printStackTrace();
                    });
                } finally {
                    SwingUtilities.invokeLater(() -> {
                        runAllButton.setEnabled(true);
                        exportMenuItem.setEnabled(true);
                    });
                }
            }).start();
        });
    }
    
    private void runSelectedTests() {
        // Implementation for running selected tests
        logMessage("Selected test execution not yet implemented", WarnoTheme.WARNING_ORANGE);
    }
    
    private void exportResults() {
        if (lastResults == null) {
            JOptionPane.showMessageDialog(this, "No test results to export", "Export Results", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new java.io.File("test-results-" + System.currentTimeMillis() + ".txt"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                exportResultsToFile(fileChooser.getSelectedFile());
                logMessage("Results exported to: " + fileChooser.getSelectedFile().getName(), WarnoTheme.SUCCESS_GREEN);
            } catch (Exception e) {
                logMessage("Export failed: " + e.getMessage(), WarnoTheme.ERROR_RED);
            }
        }
    }

    private void exportResultsToFile(java.io.File file) throws java.io.IOException {
        try (java.io.PrintWriter writer = new java.io.PrintWriter(file)) {
            writer.println("WARNO Mod Maker - E2E Test Results");
            writer.println("Generated: " + new java.util.Date());
            writer.println("=" + "=".repeat(50));
            writer.println();

            // Summary
            writer.println("SUMMARY:");
            writer.println("Total Tests: " + lastResults.results.size());
            writer.println("Passed: " + lastResults.getPassedCount());
            writer.println("Failed: " + lastResults.getFailedCount());
            writer.println("Total Time: " + lastResults.getTotalDuration() + "ms");
            writer.println("Average Time: " + timeFormat.format(lastResults.getAverageDuration()) + "ms");
            writer.println("Assertions: " + TestAssert.getAssertionCount());
            writer.println();

            // Results by category
            Map<TestRunner.TestCategory, java.util.List<TestRunner.TestResult>> resultsByCategory = lastResults.getResultsByCategory();
            for (Map.Entry<TestRunner.TestCategory, java.util.List<TestRunner.TestResult>> entry : resultsByCategory.entrySet()) {
                writer.println(entry.getKey().getDisplayName().toUpperCase() + ":");
                for (TestRunner.TestResult result : entry.getValue()) {
                    String status = result.passed ? "PASS" : "FAIL";
                    writer.println("  " + status + " - " + result.testName + " (" + result.durationMs + "ms)");
                    if (!result.passed && result.failure != null) {
                        writer.println("    Error: " + result.failure.getMessage());
                    }
                }
                writer.println();
            }

            // Detailed log
            writer.println("DETAILED LOG:");
            writer.println("-".repeat(50));
            writer.println(logPane.getText());
        }
    }
    
    private void clearLog() {
        logPane.setText("");
    }

    private void showDebugConsole() {
        if (debugConsole != null) {
            debugConsole.setVisible(true);
            debugConsole.toFront();
            debugConsole.requestFocus();
        }
    }
    
    private void logMessage(String message, Color color) {
        SwingUtilities.invokeLater(() -> {
            try {
                javax.swing.text.StyledDocument doc = logPane.getStyledDocument();
                javax.swing.text.SimpleAttributeSet attrs = new javax.swing.text.SimpleAttributeSet();
                javax.swing.text.StyleConstants.setForeground(attrs, color);
                javax.swing.text.StyleConstants.setFontFamily(attrs, Font.MONOSPACED);
                javax.swing.text.StyleConstants.setFontSize(attrs, 12);

                doc.insertString(doc.getLength(), message + "\n", attrs);
                logPane.setCaretPosition(doc.getLength());
            } catch (javax.swing.text.BadLocationException e) {
                // Fallback to simple append
                logPane.setText(logPane.getText() + message + "\n");
            }
        });
    }
    
    // TestProgressListener implementation
    @Override
    public void onTestStarted(String testName, TestRunner.TestCategory category) {
        SwingUtilities.invokeLater(() -> {
            currentTestLabel.setText("Running: " + testName);
            currentTestLabel.setForeground(Color.WHITE); // Ensure white text
            // Use a darker, more readable orange/amber color for running tests
            logMessage("> " + testName, new Color(204, 102, 0)); // Dark orange
        });
    }

    @Override
    public void onTestCompleted(TestRunner.TestResult result) {
        SwingUtilities.invokeLater(() -> {
            completedTests++;

            // Update progress
            overallProgress.setValue(completedTests);
            overallProgress.setString(completedTests + " / " + totalTests);

            // Use more readable colors
            Color resultColor = result.passed ? new Color(0, 128, 0) : new Color(178, 34, 34); // Dark green / Dark red
            String resultSymbol = result.passed ? "[PASS]" : "[FAIL]";
            String timeStr = timeFormat.format(result.durationMs) + "ms";

            logMessage(String.format("%s %s (%s)", resultSymbol, result.testName, timeStr), resultColor);

            if (!result.passed && result.failure != null) {
                logMessage("  Error: " + result.failure.getMessage(), new Color(178, 34, 34)); // Dark red

                // Show stack trace for debugging
                StringWriter sw = new StringWriter();
                result.failure.printStackTrace(new PrintWriter(sw));
                String stackTrace = sw.toString();
                if (stackTrace.length() > 500) {
                    stackTrace = stackTrace.substring(0, 500) + "... (truncated)";
                }
                logMessage("  " + stackTrace.replace("\n", "\n  "), Color.GRAY);
            }
        });
    }
    
    @Override
    public void onAllTestsCompleted(TestRunner.TestResults results) {
        SwingUtilities.invokeLater(() -> {
            this.lastResults = results;
            
            currentTestLabel.setText("Tests completed");
            currentTestLabel.setForeground(Color.WHITE); // Ensure white text
            overallProgress.setValue(100);
            overallProgress.setString("Completed");
            // Progress bar stays green throughout
            
            // Update summary
            updateSummary(results);
            
            // Log final results
            logMessage("\n=== TEST EXECUTION COMPLETE ===", new Color(0, 102, 153)); // Dark blue

            // Determine summary color based on results
            Color summaryColor;
            String summaryMessage;

            if (results.getFailedCount() == 0) {
                // All tests passed - Dark Green
                summaryColor = new Color(0, 128, 0);
                summaryMessage = "*** ALL TESTS PASSED! ***";
            } else if (results.getFailedCount() >= results.getPassedCount()) {
                // Majority failed - Dark Red
                summaryColor = new Color(178, 34, 34);
                summaryMessage = "*** " + results.getFailedCount() + " TESTS FAILED ***";
            } else {
                // Partial failure - Dark Orange
                summaryColor = new Color(204, 102, 0);
                summaryMessage = "*** " + results.getFailedCount() + " TESTS FAILED (PARTIAL) ***";
            }

            logMessage(summaryMessage, summaryColor);
            
            logMessage(String.format("Total: %d tests, %d passed, %d failed", 
                results.results.size(), results.getPassedCount(), results.getFailedCount()), Color.WHITE);
            logMessage(String.format("Execution time: %s ms (avg: %s ms per test)", 
                numberFormat.format(results.getTotalDuration()), 
                timeFormat.format(results.getAverageDuration())), Color.WHITE);
            logMessage(String.format("Assertions: %d total, %d passed",
                TestAssert.getAssertionCount(), TestAssert.getPassedAssertions()), Color.BLACK);
        });
    }
    
    private void updateSummary(TestRunner.TestResults results) {
        totalTestsLabel.setText("Total Tests: " + numberFormat.format(results.results.size()));
        passedTestsLabel.setText("Passed: " + numberFormat.format(results.getPassedCount()));
        failedTestsLabel.setText("Failed: " + numberFormat.format(results.getFailedCount()));
        totalTimeLabel.setText("Total Time: " + numberFormat.format(results.getTotalDuration()) + "ms");
        avgTimeLabel.setText("Avg Time: " + timeFormat.format(results.getAverageDuration()) + "ms");
        assertionsLabel.setText("Assertions: " + numberFormat.format(TestAssert.getAssertionCount()));
        
        // Update colors based on results
        if (results.getFailedCount() == 0) {
            // All tests passed - Dark Green
            passedTestsLabel.setForeground(new Color(0, 128, 0));
            failedTestsLabel.setForeground(Color.WHITE);
            totalTestsLabel.setForeground(new Color(0, 128, 0));
        } else if (results.getFailedCount() >= results.getPassedCount()) {
            // Majority failed - Dark Red
            passedTestsLabel.setForeground(Color.WHITE);
            failedTestsLabel.setForeground(new Color(178, 34, 34));
            totalTestsLabel.setForeground(new Color(178, 34, 34));
        } else {
            // Partial failure - Dark Orange
            passedTestsLabel.setForeground(Color.WHITE);
            failedTestsLabel.setForeground(new Color(204, 102, 0));
            totalTestsLabel.setForeground(new Color(204, 102, 0));
        }
    }

    /**
     * Custom tree cell renderer for better appearance and consistent theming
     */
    private static class TestTreeCellRenderer extends javax.swing.tree.DefaultTreeCellRenderer {

        public TestTreeCellRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {

            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

            // Set consistent colors and remove weird highlighting
            if (selected) {
                setBackground(WarnoTheme.ACCENT_BLUE);
                setForeground(Color.WHITE);
            } else {
                setBackground(WarnoTheme.TACTICAL_GREEN_LIGHT);
                setForeground(Color.WHITE);
            }

            // Remove all borders to clean up appearance
            setBorderSelectionColor(null);
            setBorder(null);

            // Set consistent font
            setFont(getFont().deriveFont(Font.PLAIN, 12f));

            // Ensure proper opacity
            setOpaque(true);

            return this;
        }

        @Override
        public void paintComponent(Graphics g) {
            // Fill background completely to avoid artifacts
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            super.paintComponent(g);
        }
    }
}
