package com.warnomodmaker.gui.components;

import com.warnomodmaker.gui.theme.WarnoTheme;
import com.warnomodmaker.model.FileTabState;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.text.DecimalFormat;

public class StatusBar extends JPanel {

    private JLabel fileInfoLabel;
    private JLabel objectCountLabel;
    private JLabel modificationCountLabel;
    private JLabel memoryLabel;
    private JProgressBar operationProgress;
    private JLabel operationLabel;

    private Timer memoryUpdateTimer;
    private DecimalFormat memoryFormat = new DecimalFormat("#.#");

    public StatusBar() {
        initializeComponents();
        setupLayout();
        startMemoryMonitoring();
    }

    private void initializeComponents() {
        setPreferredSize(new Dimension(0, 25));
        setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        setBackground(WarnoTheme.TACTICAL_GREEN_LIGHT);

        // File information
        fileInfoLabel = new JLabel("No file loaded");
        fileInfoLabel.setForeground(Color.WHITE);

        // Object count
        objectCountLabel = new JLabel("0 objects");
        objectCountLabel.setForeground(WarnoTheme.ACCENT_BLUE_LIGHT);

        // Modification count
        modificationCountLabel = new JLabel("0 modifications");
        modificationCountLabel.setForeground(WarnoTheme.WARNING_ORANGE);

        // Memory usage
        memoryLabel = new JLabel("Memory: 0 MB");
        memoryLabel.setForeground(Color.LIGHT_GRAY);
        memoryLabel.setFont(memoryLabel.getFont().deriveFont(Font.PLAIN, 11f));

        // Operation progress
        operationProgress = new JProgressBar();
        operationProgress.setVisible(false);
        operationProgress.setPreferredSize(new Dimension(100, 16));
        operationProgress.setStringPainted(true);

        // Operation label
        operationLabel = new JLabel("");
        operationLabel.setForeground(WarnoTheme.ACCENT_BLUE_LIGHT);
        operationLabel.setFont(operationLabel.getFont().deriveFont(Font.ITALIC, 11f));
    }

    private void setupLayout() {
        setLayout(new BorderLayout());

        // Left panel with file info
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        leftPanel.setOpaque(false);
        leftPanel.add(fileInfoLabel);
        leftPanel.add(createSeparator());
        leftPanel.add(objectCountLabel);
        leftPanel.add(createSeparator());
        leftPanel.add(modificationCountLabel);

        // Center panel with operation info
        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 2));
        centerPanel.setOpaque(false);
        centerPanel.add(operationLabel);
        centerPanel.add(operationProgress);

        // Right panel with memory info
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 2));
        rightPanel.setOpaque(false);
        rightPanel.add(memoryLabel);

        add(leftPanel, BorderLayout.WEST);
        add(centerPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);
    }

    private JLabel createSeparator() {
        JLabel separator = new JLabel("|");
        separator.setForeground(WarnoTheme.TACTICAL_GREEN_LIGHT.brighter());
        return separator;
    }

    private void startMemoryMonitoring() {
        memoryUpdateTimer = new Timer(2000, e -> updateMemoryInfo());
        memoryUpdateTimer.start();
    }

    private void updateMemoryInfo() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        double usedMB = usedMemory / (1024.0 * 1024.0);
        double totalMB = totalMemory / (1024.0 * 1024.0);

        String memoryText = String.format("Memory: %s / %s MB",
            memoryFormat.format(usedMB),
            memoryFormat.format(totalMB));

        memoryLabel.setText(memoryText);

        // Change color based on memory usage
        double usagePercent = (double) usedMemory / totalMemory;
        if (usagePercent > 0.8) {
            memoryLabel.setForeground(WarnoTheme.ERROR_RED);
        } else if (usagePercent > 0.6) {
            memoryLabel.setForeground(WarnoTheme.WARNING_ORANGE);
        } else {
            memoryLabel.setForeground(Color.LIGHT_GRAY);
        }
    }

    public void updateFileInfo(FileTabState tabState) {
        if (tabState == null || !tabState.hasData()) {
            fileInfoLabel.setText("No file loaded");
            objectCountLabel.setText("0 objects");
            modificationCountLabel.setText("0 modifications");
        } else {
            String fileName = tabState.getFile() != null ? tabState.getFile().getName() : "Untitled";
            String fileType = tabState.getFileType().getDisplayName();

            fileInfoLabel.setText(fileName + " (" + fileType + ")");

            int objectCount = tabState.getUnitDescriptors().size();
            objectCountLabel.setText(objectCount + " objects");

            int modCount = tabState.getModificationTracker().getModificationCount();
            modificationCountLabel.setText(modCount + " modifications");

            // Update colors based on modification status
            if (tabState.isModified()) {
                modificationCountLabel.setForeground(WarnoTheme.WARNING_ORANGE);
            } else {
                modificationCountLabel.setForeground(WarnoTheme.ACCENT_BLUE_LIGHT);
            }
        }
    }

    public void showOperation(String operationName) {
        operationLabel.setText(operationName);
        operationProgress.setVisible(true);
        operationProgress.setIndeterminate(true);
        revalidate();
    }

    public void showOperation(String operationName, int progress, int maximum) {
        operationLabel.setText(operationName);
        operationProgress.setVisible(true);
        operationProgress.setIndeterminate(false);
        operationProgress.setMaximum(maximum);
        operationProgress.setValue(progress);
        operationProgress.setString(progress + "/" + maximum);
        revalidate();
    }

    public void hideOperation() {
        operationLabel.setText("");
        operationProgress.setVisible(false);
        revalidate();
    }

    public void showMessage(String message, MessageType type) {
        operationLabel.setText(message);
        operationProgress.setVisible(false);

        switch (type) {
            case SUCCESS:
                operationLabel.setForeground(WarnoTheme.SUCCESS_GREEN);
                break;
            case WARNING:
                operationLabel.setForeground(WarnoTheme.WARNING_ORANGE);
                break;
            case ERROR:
                operationLabel.setForeground(WarnoTheme.ERROR_RED);
                break;
            default:
                operationLabel.setForeground(WarnoTheme.ACCENT_BLUE_LIGHT);
                break;
        }

        // Auto-hide message after 5 seconds
        Timer hideTimer = new Timer(5000, e -> {
            operationLabel.setText("");
            operationLabel.setForeground(WarnoTheme.ACCENT_BLUE_LIGHT);
        });
        hideTimer.setRepeats(false);
        hideTimer.start();

        revalidate();
    }

    public enum MessageType {
        INFO, SUCCESS, WARNING, ERROR
    }

    public void dispose() {
        if (memoryUpdateTimer != null) {
            memoryUpdateTimer.stop();
        }
    }
}
