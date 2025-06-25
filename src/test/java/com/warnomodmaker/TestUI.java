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
    private JButton exportButton;
    private JMenuItem exportMenuItem;
    private JButton clearLogButton;
    
    private TestRunner testRunner;
    private ComprehensiveE2ETest testInstance;
    private TestRunner.TestResults lastResults;
    private int totalTests = 0;
    private int completedTests = 0;

    private DecimalFormat timeFormat = new DecimalFormat("#,##0.0");
    private DecimalFormat numberFormat = new DecimalFormat("#,##0");
    
    public TestUI(ComprehensiveE2ETest testInstance) {
        super("WARNO Mod Maker - E2E Test Suite");
        this.testInstance = testInstance;
        this.testRunner = new TestRunner();
        this.testRunner.addProgressListener(this);
        
        initializeUI();
        setupTests();
        setupEventHandlers();
        
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);
    }
    
    private void initializeUI() {
        setLayout(new BorderLayout());

        // Create menu bar
        setJMenuBar(createMenuBar());

        // Create main panels
        createTestTreePanel();
        createLogPanel();
        createControlPanel();
        createSummaryPanel();

        // Layout main components
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setLeftComponent(createLeftPanel());
        mainSplit.setRightComponent(createRightPanel());
        mainSplit.setDividerLocation(400);
        mainSplit.setResizeWeight(0.3);

        add(mainSplit, BorderLayout.CENTER);
        add(createControlPanel(), BorderLayout.SOUTH);
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBackground(WarnoTheme.TACTICAL_GREEN_DARK);

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
        leftPanel.setBorder(BorderFactory.createTitledBorder("Test Categories"));
        leftPanel.setBackground(WarnoTheme.TACTICAL_GREEN_DARK);
        
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
        treeScrollPane.setBorder(BorderFactory.createLoweredBevelBorder());
        treeScrollPane.getViewport().setBackground(WarnoTheme.TACTICAL_GREEN_LIGHT);
        leftPanel.add(treeScrollPane, BorderLayout.CENTER);
        
        // Summary panel
        leftPanel.add(createSummaryPanel(), BorderLayout.SOUTH);
        
        return leftPanel;
    }
    
    private JPanel createRightPanel() {
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("Test Execution Log"));
        rightPanel.setBackground(WarnoTheme.TACTICAL_GREEN_DARK);
        
        // Log area
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.WHITE);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        
        logScrollPane = new JScrollPane(logArea);
        logScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        rightPanel.add(logScrollPane, BorderLayout.CENTER);
        
        // Status panel
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBackground(WarnoTheme.TACTICAL_GREEN_DARK);
        statusPanel.setBorder(BorderFactory.createTitledBorder("Current Status"));
        
        currentTestLabel = new JLabel("Ready to run tests");
        currentTestLabel.setForeground(Color.WHITE);
        currentTestLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        overallProgress = new JProgressBar();
        overallProgress.setStringPainted(true);
        overallProgress.setString("Ready");
        overallProgress.setBackground(WarnoTheme.TACTICAL_GREEN_LIGHT);
        overallProgress.setForeground(WarnoTheme.ACCENT_BLUE);
        
        statusPanel.add(currentTestLabel, BorderLayout.NORTH);
        statusPanel.add(overallProgress, BorderLayout.CENTER);
        
        rightPanel.add(statusPanel, BorderLayout.SOUTH);
        
        return rightPanel;
    }
    
    private JPanel createSummaryPanel() {
        summaryPanel = new JPanel(new GridLayout(3, 2, 5, 5));
        summaryPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(WarnoTheme.ACCENT_BLUE), 
            "Test Summary", 
            TitledBorder.DEFAULT_JUSTIFICATION, 
            TitledBorder.DEFAULT_POSITION, 
            null, 
            Color.WHITE));
        summaryPanel.setBackground(WarnoTheme.TACTICAL_GREEN_DARK);
        
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
        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        return label;
    }
    
    private JPanel createControlPanel() {
        JPanel controlPanel = new JPanel(new FlowLayout());
        controlPanel.setBackground(WarnoTheme.TACTICAL_GREEN_DARK);
        controlPanel.setBorder(BorderFactory.createRaisedBevelBorder());
        
        runAllButton = new JButton("Run All Tests");
        runAllButton.setBackground(WarnoTheme.SUCCESS_GREEN);
        runAllButton.setForeground(Color.WHITE);
        runAllButton.setFont(runAllButton.getFont().deriveFont(Font.BOLD));
        
        runSelectedButton = new JButton("Run Selected");
        runSelectedButton.setBackground(WarnoTheme.ACCENT_BLUE);
        runSelectedButton.setForeground(Color.WHITE);
        runSelectedButton.setEnabled(false);
        
        exportButton = new JButton("Export Results");
        exportButton.setBackground(WarnoTheme.WARNING_ORANGE);
        exportButton.setForeground(Color.WHITE);
        exportButton.setEnabled(false);
        
        clearLogButton = new JButton("Clear Log");
        clearLogButton.setBackground(WarnoTheme.TACTICAL_GREEN_LIGHT);
        clearLogButton.setForeground(Color.WHITE);
        
        controlPanel.add(runAllButton);
        controlPanel.add(runSelectedButton);
        controlPanel.add(Box.createHorizontalStrut(20));
        controlPanel.add(exportButton);
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

        logMessage("Test structure loaded: " + rootNode.getChildCount() + " categories", Color.CYAN);
    }
    
    private void setupEventHandlers() {
        runAllButton.addActionListener(e -> runAllTests());
        runSelectedButton.addActionListener(e -> runSelectedTests());
        exportButton.addActionListener(e -> exportResults());
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

            logMessage("=== WARNO Mod Maker E2E Test Suite ===", WarnoTheme.ACCENT_BLUE);
            logMessage("Starting comprehensive test execution...", Color.WHITE);
            logMessage("Total tests to run: " + totalTests + "\n", Color.WHITE);

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
                        exportButton.setEnabled(true);
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
            writer.println(logArea.getText());
        }
    }
    
    private void clearLog() {
        logArea.setText("");
    }
    
    private void logMessage(String message, Color color) {
        SwingUtilities.invokeLater(() -> {
            logArea.setForeground(color);
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    
    // TestProgressListener implementation
    @Override
    public void onTestStarted(String testName, TestRunner.TestCategory category) {
        SwingUtilities.invokeLater(() -> {
            currentTestLabel.setText("Running: " + testName);
            logMessage("> " + testName, WarnoTheme.ACCENT_BLUE);
        });
    }

    @Override
    public void onTestCompleted(TestRunner.TestResult result) {
        SwingUtilities.invokeLater(() -> {
            completedTests++;

            // Update progress
            overallProgress.setValue(completedTests);
            overallProgress.setString(completedTests + " / " + totalTests);

            Color resultColor = result.passed ? WarnoTheme.SUCCESS_GREEN : WarnoTheme.ERROR_RED;
            String resultSymbol = result.passed ? "[PASS]" : "[FAIL]";
            String timeStr = timeFormat.format(result.durationMs) + "ms";

            logMessage(String.format("%s %s (%s)", resultSymbol, result.testName, timeStr), resultColor);

            if (!result.passed && result.failure != null) {
                logMessage("  Error: " + result.failure.getMessage(), WarnoTheme.ERROR_RED);

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
            overallProgress.setValue(100);
            overallProgress.setString("Completed");
            
            // Update summary
            updateSummary(results);
            
            // Log final results
            logMessage("\n=== TEST EXECUTION COMPLETE ===", WarnoTheme.ACCENT_BLUE);
            
            if (results.getFailedCount() == 0) {
                logMessage("*** ALL TESTS PASSED! ***", WarnoTheme.SUCCESS_GREEN);
            } else {
                logMessage("*** " + results.getFailedCount() + " TESTS FAILED ***", WarnoTheme.ERROR_RED);
            }
            
            logMessage(String.format("Total: %d tests, %d passed, %d failed", 
                results.results.size(), results.getPassedCount(), results.getFailedCount()), Color.WHITE);
            logMessage(String.format("Execution time: %s ms (avg: %s ms per test)", 
                numberFormat.format(results.getTotalDuration()), 
                timeFormat.format(results.getAverageDuration())), Color.WHITE);
            logMessage(String.format("Assertions: %d total, %d passed",
                TestAssert.getAssertionCount(), TestAssert.getPassedAssertions()), Color.WHITE);
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
            passedTestsLabel.setForeground(WarnoTheme.SUCCESS_GREEN);
            failedTestsLabel.setForeground(Color.WHITE);
        } else {
            passedTestsLabel.setForeground(Color.WHITE);
            failedTestsLabel.setForeground(WarnoTheme.ERROR_RED);
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
