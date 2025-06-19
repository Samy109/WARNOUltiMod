package com.warnomodmaker.gui;

import com.warnomodmaker.model.*;
import com.warnomodmaker.model.NDFValue.ObjectValue;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ProfileLoadDialog extends JDialog {
    private final ModProfile profile;
    private final List<ObjectValue> unitDescriptors;
    private final ModificationTracker modificationTracker;
    private final List<ValidationResult> validationResults;
    private final MainWindow parentWindow;
    private boolean applied = false;
    private JTable validationTable;
    private ValidationTableModel tableModel;
    private JLabel statusLabel;
    private JButton applyButton;
    private JButton cancelButton;
    private JButton fixPathsButton;


    public static class ValidationResult {
        public ModificationRecord modification; // Non-final to allow auto-fix updates
        public final boolean isValid;
        public final String issue;
        public final String suggestedFix;
        public boolean shouldApply;

        public ValidationResult(ModificationRecord modification, boolean isValid, String issue, String suggestedFix) {
            this.modification = modification;
            this.isValid = isValid;
            this.issue = issue;
            this.suggestedFix = suggestedFix;
            this.shouldApply = isValid; // Default to applying valid modifications
        }
    }


    public ProfileLoadDialog(JFrame parent, ModProfile profile, List<ObjectValue> unitDescriptors, ModificationTracker modificationTracker) {
        super(parent, "Load Profile: " + profile.getProfileName(), true);

        this.profile = profile;
        this.unitDescriptors = unitDescriptors;
        this.modificationTracker = modificationTracker;
        this.validationResults = new ArrayList<>();
        this.parentWindow = (MainWindow) parent;

        initializeGUI();
        validateProfile();
    }


    private void initializeGUI() {
        setSize(900, 600);
        setLocationRelativeTo(getParent());
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Profile info panel
        JPanel infoPanel = createInfoPanel();
        mainPanel.add(infoPanel, BorderLayout.NORTH);

        // Validation table
        tableModel = new ValidationTableModel();
        validationTable = new JTable(tableModel) {
            @Override
            public String getToolTipText(java.awt.event.MouseEvent e) {
                int row = rowAtPoint(e.getPoint());
                int col = columnAtPoint(e.getPoint());
                if (row >= 0 && row < validationResults.size()) {
                    ValidationResult result = validationResults.get(row);
                    ModificationRecord mod = result.modification;
                    switch (col) {
                        case 1: return "Unit: " + mod.getUnitName();
                        case 2: return "Property: " + mod.getPropertyPath();
                        case 4:
                            if (result.isValid && result.issue.equals("Ready to apply")) {
                                return String.format("<html>Change: %s -> %s<br>Type: %s<br>Timestamp: %s</html>",
                                    mod.getOldValue(), mod.getNewValue(), mod.getModificationType().getDisplayName(), mod.getFormattedTimestamp());
                            } else {
                                return String.format("<html>Issue: %s<br>%s</html>",
                                    result.issue, result.suggestedFix != null ? "Suggestion: " + result.suggestedFix : "No suggestions available");
                            }
                    }
                }
                return super.getToolTipText(e);
            }
        };
        validationTable.setRowHeight(25);

        // Configure column sizing for proper resizing behavior
        validationTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        TableColumnModel columnModel = validationTable.getColumnModel();

        // Apply column (checkbox) - fixed small width
        columnModel.getColumn(0).setMinWidth(50);
        columnModel.getColumn(0).setPreferredWidth(50);
        columnModel.getColumn(0).setMaxWidth(60);

        // Unit column - moderate width, can grow
        columnModel.getColumn(1).setMinWidth(100);
        columnModel.getColumn(1).setPreferredWidth(120);

        // Property column - should grow with dialog, this is the main content
        columnModel.getColumn(2).setMinWidth(200);
        columnModel.getColumn(2).setPreferredWidth(300);

        // Status column - fixed moderate width
        columnModel.getColumn(3).setMinWidth(60);
        columnModel.getColumn(3).setPreferredWidth(80);
        columnModel.getColumn(3).setMaxWidth(100);

        // Issue/Suggestion column - large, can grow
        columnModel.getColumn(4).setMinWidth(250);
        columnModel.getColumn(4).setPreferredWidth(400);

        // Custom renderer for status column
        validationTable.getColumnModel().getColumn(3).setCellRenderer(new StatusCellRenderer());

        JScrollPane scrollPane = new JScrollPane(validationTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Modifications"));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Status and button panel
        JPanel bottomPanel = createBottomPanel();
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }


    private JPanel createInfoPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Profile Information"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Profile details
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1;
        panel.add(new JLabel(profile.getProfileName()), gbc);

        gbc.gridx = 2; gbc.gridy = 0;
        panel.add(new JLabel("Created:"), gbc);
        gbc.gridx = 3;
        panel.add(new JLabel(profile.getFormattedCreatedDate()), gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Source File:"), gbc);
        gbc.gridx = 1;
        panel.add(new JLabel(profile.getSourceFileName()), gbc);

        gbc.gridx = 2; gbc.gridy = 1;
        panel.add(new JLabel("Modifications:"), gbc);
        gbc.gridx = 3;
        panel.add(new JLabel(String.valueOf(profile.getModificationCount())), gbc);

        if (!profile.getDescription().isEmpty()) {
            gbc.gridx = 0; gbc.gridy = 2;
            panel.add(new JLabel("Description:"), gbc);
            gbc.gridx = 1; gbc.gridwidth = 3;
            panel.add(new JLabel(profile.getDescription()), gbc);
        }

        return panel;
    }


    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Status label
        statusLabel = new JLabel("Validating modifications...");
        panel.add(statusLabel, BorderLayout.WEST);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        fixPathsButton = new JButton("Auto-Fix Paths");
        fixPathsButton.addActionListener(this::autoFixPaths);
        fixPathsButton.setEnabled(false);
        buttonPanel.add(fixPathsButton);

        applyButton = new JButton("Apply Selected");
        applyButton.addActionListener(this::applyModifications);
        applyButton.setEnabled(false);
        buttonPanel.add(applyButton);

        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(cancelButton);

        panel.add(buttonPanel, BorderLayout.EAST);

        return panel;
    }


    private void validateProfile() {
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                validationResults.clear();

                for (ModificationRecord modification : profile.getModifications()) {
                    ValidationResult result = validateModification(modification);
                    validationResults.add(result);
                }

                return null;
            }

            @Override
            protected void done() {
                tableModel.fireTableDataChanged();
                updateStatus();
                applyButton.setEnabled(true);

                // Enable auto-fix if there are fixable issues
                boolean hasFixableIssues = validationResults.stream()
                    .anyMatch(r -> !r.isValid && r.suggestedFix != null && !r.suggestedFix.isEmpty());
                fixPathsButton.setEnabled(hasFixableIssues);
            }
        };

        worker.execute();
    }


    private ValidationResult validateModification(ModificationRecord modification) {
        String unitName = modification.getUnitName();
        String propertyPath = modification.getPropertyPath();
        ObjectValue unit = findUnitByName(unitName);
        if (unit == null) {
            String suggestedUnit = findSimilarUnitName(unitName);
            String issue = String.format("Unit '%s' not found in current file", unitName);
            String suggestion = suggestedUnit != null ? "Try: " + suggestedUnit : "No similar units found";
            return new ValidationResult(modification, false, issue, suggestion);
        }
        // Use file type for proper property validation - ALIGNED with single/mass modifications!
        NDFValue.NDFFileType fileType = parentWindow.getCurrentFileType();
        if (!PropertyUpdater.hasProperty(unit, propertyPath, fileType)) {
            String suggestedPath = findSimilarPropertyPath(unit, propertyPath);
            String issue = String.format("Property '%s' not found in unit '%s'", propertyPath, unitName);
            String suggestion = suggestedPath != null ? "Try: " + suggestedPath : "No similar properties found";
            return new ValidationResult(modification, false, issue, suggestion);
        }
        NDFValue currentValue = PropertyUpdater.getPropertyValue(unit, propertyPath, fileType);
        if (currentValue != null) {
            String currentValueStr = getValueForComparison(currentValue);
            String expectedOldValue = modification.getOldValue();

            // If the current value doesn't match the expected old value, warn the user
            if (!currentValueStr.equals(expectedOldValue)) {
                String issue = String.format("Property value has changed. Expected: %s, Found: %s", expectedOldValue, currentValueStr);
                String suggestion = "Value will be updated to: " + modification.getNewValue();
                return new ValidationResult(modification, true, issue, suggestion); // Still valid, but with warning
            }
        }

        // Validation passed
        return new ValidationResult(modification, true, "Ready to apply", null);
    }


    private ObjectValue findUnitByName(String unitName) {
        for (ObjectValue unit : unitDescriptors) {
            if (unitName.equals(unit.getInstanceName())) {
                return unit;
            }
        }
        return null;
    }


    private String findSimilarUnitName(String targetName) {
        if (targetName == null || targetName.trim().isEmpty()) {
            return null;
        }

        String lowerTarget = targetName.toLowerCase().trim();
        List<ScoredMatch> candidates = new ArrayList<>();

        for (ObjectValue unit : unitDescriptors) {
            String unitName = unit.getInstanceName();
            if (unitName != null && !unitName.trim().isEmpty()) {
                double score = calculateUnitNameSimilarity(lowerTarget, unitName.toLowerCase());
                if (score > 0.3) { // Only consider reasonably similar names
                    candidates.add(new ScoredMatch(unitName, score));
                }
            }
        }

        // Sort by score (highest first) and return the best match
        candidates.sort((a, b) -> Double.compare(b.score, a.score));
        return candidates.isEmpty() ? null : candidates.get(0).value;
    }


    private double calculateUnitNameSimilarity(String target, String candidate) {
        // 1. Exact match
        if (target.equals(candidate)) {
            return 1.0;
        }

        // 2. Contains match (high score for substring matches)
        if (candidate.contains(target)) {
            return 0.9;
        }
        if (target.contains(candidate)) {
            return 0.85;
        }

        // 3. Levenshtein distance (fuzzy matching for typos)
        double levenshteinScore = 1.0 - (double) levenshteinDistance(target, candidate) / Math.max(target.length(), candidate.length());

        // 4. Token-based matching (for multi-word names)
        double tokenScore = calculateTokenSimilarity(target, candidate);

        // 5. Common prefix/suffix matching
        double affixScore = calculateAffixSimilarity(target, candidate);

        // Combine scores with weights
        return Math.max(levenshteinScore * 0.4 + tokenScore * 0.4 + affixScore * 0.2, 0.0);
    }


    private String findSimilarPropertyPath(ObjectValue unit, String targetPath) {
        if (targetPath == null || targetPath.trim().isEmpty()) {
            return null;
        }

        // First, try converting specific array indices to wildcards
        String wildcardPath = normalizeArrayIndices(targetPath);
        if (!wildcardPath.equals(targetPath) && hasPropertyWithWildcards(unit, wildcardPath)) {
            return wildcardPath;
        }

        PropertyScanner scanner = new PropertyScanner(List.of(unit));
        scanner.scanProperties();

        String lowerTarget = targetPath.toLowerCase().trim();
        List<ScoredMatch> candidates = new ArrayList<>();

        for (PropertyScanner.PropertyInfo property : scanner.getDiscoveredProperties().values()) {
            double score = calculatePropertyPathSimilarity(lowerTarget, property.path.toLowerCase());
            if (score > 0.4) { // Higher threshold for property paths
                candidates.add(new ScoredMatch(property.path, score));
            }
        }

        // Sort by score (highest first) and return the best match
        candidates.sort((a, b) -> Double.compare(b.score, a.score));
        return candidates.isEmpty() ? null : candidates.get(0).value;
    }


    private double calculatePropertyPathSimilarity(String target, String candidate) {
        // 1. Exact match
        if (target.equals(candidate)) {
            return 1.0;
        }

        // 2. Path structure analysis - split by dots and compare segments
        String[] targetParts = target.split("\\.");
        String[] candidateParts = candidate.split("\\.");

        double structuralScore = calculatePathStructuralSimilarity(targetParts, candidateParts);

        // 3. Array index flexibility - treat [X] as equivalent
        String normalizedTarget = normalizeArrayIndices(target);
        String normalizedCandidate = normalizeArrayIndices(candidate);
        double normalizedScore = normalizedTarget.equals(normalizedCandidate) ? 0.95 : 0.0;

        // 4. Substring matching for partial paths
        double substringScore = 0.0;
        if (candidate.contains(target)) {
            substringScore = 0.8;
        } else if (target.contains(candidate)) {
            substringScore = 0.75;
        }

        // 5. Levenshtein distance for typo tolerance
        double levenshteinScore = 1.0 - (double) levenshteinDistance(target, candidate) / Math.max(target.length(), candidate.length());

        // 6. End-of-path matching (property name similarity)
        String targetEnd = targetParts[targetParts.length - 1];
        String candidateEnd = candidateParts[candidateParts.length - 1];
        double endScore = calculateTokenSimilarity(targetEnd, candidateEnd);

        // Combine scores with weights favoring structural and normalized matches
        return Math.max(Math.max(normalizedScore, structuralScore * 0.6 + endScore * 0.4),
                       Math.max(substringScore, levenshteinScore * 0.3));
    }


    private void updateStatus() {
        int total = validationResults.size();
        int valid = (int) validationResults.stream().mapToInt(r -> r.isValid ? 1 : 0).sum();
        int selected = (int) validationResults.stream().mapToInt(r -> r.shouldApply ? 1 : 0).sum();

        statusLabel.setText(String.format("Total: %d, Valid: %d, Selected for application: %d", total, valid, selected));
    }


    private void autoFixPaths(ActionEvent e) {
        List<ValidationResult> fixableResults = validationResults.stream()
            .filter(r -> !r.isValid && r.suggestedFix != null && !r.suggestedFix.isEmpty())
            .collect(Collectors.toList());

        if (fixableResults.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No fixable issues found.",
                "Auto-Fix Paths", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        StringBuilder message = new StringBuilder();
        message.append("Auto-fix will attempt to correct ").append(fixableResults.size()).append(" issues:\n\n");

        int shown = 0;
        for (ValidationResult result : fixableResults) {
            if (shown >= 5) {
                message.append("... and ").append(fixableResults.size() - shown).append(" more\n");
                break;
            }
            message.append("- ").append(result.modification.getUnitName()).append("\n");
            message.append("  ").append(result.issue).append("\n");
            message.append("  -> ").append(result.suggestedFix).append("\n\n");
            shown++;
        }

        message.append("Continue with auto-fix?");

        int choice = JOptionPane.showConfirmDialog(this, message.toString(),
            "Auto-Fix Paths", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            performAutoFix(fixableResults);
        }
    }


    private void performAutoFix(List<ValidationResult> fixableResults) {
        int fixedCount = 0;
        List<String> failedFixes = new ArrayList<>();

        for (ValidationResult result : fixableResults) {
            try {
                if (applyAutoFix(result)) {
                    fixedCount++;
                } else {
                    failedFixes.add(result.modification.getUnitName() + ": " + result.issue);
                }
            } catch (Exception ex) {
                failedFixes.add(result.modification.getUnitName() + ": " + ex.getMessage());
            }
        }

        // Re-validate after fixes
        validateProfile();
        StringBuilder resultMessage = new StringBuilder();
        resultMessage.append("Auto-fix completed!\n\n");
        resultMessage.append("Successfully fixed: ").append(fixedCount).append(" issues\n");

        if (!failedFixes.isEmpty()) {
            resultMessage.append("Failed to fix: ").append(failedFixes.size()).append(" issues\n\n");
            resultMessage.append("Failed fixes:\n");
            for (String failure : failedFixes) {
                resultMessage.append("- ").append(failure).append("\n");
            }
        }

        JOptionPane.showMessageDialog(this, resultMessage.toString(),
            "Auto-Fix Results", JOptionPane.INFORMATION_MESSAGE);
    }


    private void applyModifications(ActionEvent e) {
        List<ValidationResult> toApply = validationResults.stream()
            .filter(r -> r.shouldApply && r.isValid)
            .collect(Collectors.toList());

        if (toApply.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No valid modifications selected for application.",
                "Nothing to Apply", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int result = JOptionPane.showConfirmDialog(this,
            String.format("Apply %d modifications to the current file?\n\nThis will modify the loaded units.", toApply.size()),
            "Confirm Application", JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.YES_OPTION) {
            applySelectedModifications(toApply);
        }
    }


    private void applySelectedModifications(List<ValidationResult> toApply) {
        // Create progress dialog with static text
        JDialog progressDialog = new JDialog(this, "Applying Profile", true);
        JLabel progressLabel = new JLabel("Applying " + toApply.size() + " modifications...", SwingConstants.CENTER);
        progressLabel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        progressDialog.add(progressLabel);
        progressDialog.setSize(400, 100);
        progressDialog.setLocationRelativeTo(this);

        SwingWorker<Integer, Void> worker = new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                int appliedCount = 0;

                for (ValidationResult validationResult : toApply) {
                    ModificationRecord mod = validationResult.modification;
                    ObjectValue unit = findUnitByName(mod.getUnitName());

                    if (unit != null) {
                        try {
                            NDFValue newValue = parseValueFromString(mod.getNewValue(), mod.getNewValueType());

                            // Apply the modification with proper file type - ALIGNED with single/mass modifications!
                            NDFValue.NDFFileType fileType = parentWindow.getCurrentFileType();
                            if (PropertyUpdater.updateProperty(unit, mod.getPropertyPath(), newValue, modificationTracker, fileType)) {
                                appliedCount++;
                            }
                        } catch (Exception ex) {
                            System.err.println("Failed to apply modification: " + ex.getMessage());
                        }
                    }
                }

                return appliedCount;
            }

            @Override
            protected void done() {
                try {
                    int appliedCount = get();
                    applied = true;

                    // Update progress text to show UI refresh
                    progressLabel.setText("Refreshing UI...");

                    // Trigger UI refresh in the main window
                    if (parentWindow != null) {
                        parentWindow.refreshCurrentTab();
                    }

                    // Small delay to ensure UI refresh completes
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        // Ignore
                    }

                    // Dispose dialog and close profile dialog
                    progressDialog.dispose();
                    dispose();
                } catch (Exception ex) {
                    progressDialog.dispose();
                    JOptionPane.showMessageDialog(ProfileLoadDialog.this,
                        "Error applying profile: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        // Start worker first, then show dialog (modal dialog blocks execution)
        worker.execute();
        progressDialog.setVisible(true);
    }


    private NDFValue parseValueFromString(String valueStr, String valueType) {
        switch (valueType) {
            case "STRING":
                // Handle quote type prefixes for string values
                if (valueStr.startsWith("DQ:")) {
                    // Double quotes
                    String rawValue = valueStr.substring(3);
                    return NDFValue.createString(rawValue, true);
                } else if (valueStr.startsWith("SQ:")) {
                    // Single quotes
                    String rawValue = valueStr.substring(3);
                    return NDFValue.createString(rawValue, false);
                } else {
                    // Legacy format without prefix - default to single quotes
                    return NDFValue.createString(valueStr.replace("'", ""), false);
                }
            case "NUMBER":
                // Preserve format information when parsing numbers from profiles
                double numValue = Double.parseDouble(valueStr);
                boolean wasInteger = !valueStr.contains(".");
                return NDFValue.createNumber(numValue, wasInteger);
            case "BOOLEAN":
                return NDFValue.createBoolean(Boolean.parseBoolean(valueStr));
            case "TEMPLATE_REF":
                return NDFValue.createTemplateRef(valueStr);
            case "RESOURCE_REF":
                return NDFValue.createResourceRef(valueStr);
            case "GUID":
                return NDFValue.createGUID(valueStr);
            default:
                // For unknown types, try to parse as string with quote type detection
                if (valueStr.startsWith("DQ:") || valueStr.startsWith("SQ:")) {
                    boolean useDoubleQuotes = valueStr.startsWith("DQ:");
                    String rawValue = valueStr.substring(3);
                    return NDFValue.createString(rawValue, useDoubleQuotes);
                } else {
                    return NDFValue.createString(valueStr);
                }
        }
    }


    public boolean wasApplied() {
        return applied;
    }


    private class ValidationTableModel extends AbstractTableModel {
        private final String[] columnNames = {"Apply", "Unit", "Property", "Status", "Issue/Suggestion"};

        @Override
        public int getRowCount() {
            return validationResults.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0; // Only the Apply column is editable
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ValidationResult result = validationResults.get(rowIndex);
            ModificationRecord mod = result.modification;
            switch (columnIndex) {
                case 0: return result.shouldApply;
                case 1: return mod.getUnitName(); // No truncation - let column width determine display
                case 2: return cleanPropertyPath(mod.getPropertyPath()); // Clean up redundant prefixes
                case 3: return result.isValid ? "Valid" : "Invalid";
                case 4:
                    if (result.isValid && result.issue.equals("Ready to apply")) {
                        return String.format("%s -> %s", mod.getOldValue(), mod.getNewValue());
                    } else if (result.suggestedFix != null) {
                        return result.issue + " | " + result.suggestedFix;
                    } else {
                        return result.issue;
                    }
                default: return "";
            }
        }

        private String truncateText(String text, int maxLength) {
            if (text == null) return "";
            return text.length() > maxLength ? text.substring(0, maxLength - 3) + "..." : text;
        }


        private String cleanPropertyPath(String propertyPath) {
            if (propertyPath == null) return "";
            if (propertyPath.startsWith("ModulesDescriptors[")) {
                int dotIndex = propertyPath.indexOf('.', 19); // Look for dot after "ModulesDescriptors[XX]"
                if (dotIndex != -1) {
                    return propertyPath.substring(dotIndex + 1); // Return everything after the first dot
                }
            }

            return propertyPath; // Return as-is if no common prefix found
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                validationResults.get(rowIndex).shouldApply = (Boolean) value;
                updateStatus();
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }
    }


    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                     boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (!isSelected) {
                if ("Valid".equals(value)) {
                    setBackground(new Color(220, 255, 220)); // Light green
                    setForeground(Color.BLACK); // Dark black text for better contrast
                } else {
                    setBackground(new Color(255, 220, 220)); // Light red
                    setForeground(Color.BLACK); // Dark black text for better contrast
                }
            }

            return component;
        }
    }


    private boolean applyAutoFix(ValidationResult result) {
        String suggestion = result.suggestedFix;
        ModificationRecord oldModification = result.modification;
        if (suggestion.startsWith("Try: ")) {
            String fixedValue = suggestion.substring(5).trim();
            ModificationRecord newModification = null;

            if (result.issue.contains("Unit") && result.issue.contains("not found")) {
                // Unit name fix - create new record with corrected unit name
                newModification = new ModificationRecord(
                    fixedValue, // Fixed unit name
                    oldModification.getPropertyPath(),
                    oldModification.getOldValue(),
                    oldModification.getNewValue(),
                    oldModification.getOldValueType(),
                    oldModification.getNewValueType(),
                    oldModification.getTimestamp(),
                    oldModification.getModificationType(),
                    oldModification.getModificationDetails()
                );
            } else if (result.issue.contains("Property") && result.issue.contains("not found")) {
                // Property path fix - create new record with corrected property path
                newModification = new ModificationRecord(
                    oldModification.getUnitName(),
                    fixedValue, // Fixed property path
                    oldModification.getOldValue(),
                    oldModification.getNewValue(),
                    oldModification.getOldValueType(),
                    oldModification.getNewValueType(),
                    oldModification.getTimestamp(),
                    oldModification.getModificationType(),
                    oldModification.getModificationDetails()
                );
            }

            if (newModification != null) {
                // Replace the old modification in the result
                result.modification = newModification;
                return true;
            }
        }

        return false; // Couldn't apply the fix
    }


    private static class ScoredMatch {
        final String value;
        final double score;

        ScoredMatch(String value, double score) {
            this.value = value;
            this.score = score;
        }
    }


    private int levenshteinDistance(String a, String b) {
        if (a.length() == 0) return b.length();
        if (b.length() == 0) return a.length();

        int[][] matrix = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) {
            matrix[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            matrix[0][j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                matrix[i][j] = Math.min(Math.min(
                    matrix[i - 1][j] + 1,      // deletion
                    matrix[i][j - 1] + 1),     // insertion
                    matrix[i - 1][j - 1] + cost); // substitution
            }
        }

        return matrix[a.length()][b.length()];
    }


    private double calculateTokenSimilarity(String target, String candidate) {
        String[] targetTokens = target.split("[\\s_-]+");
        String[] candidateTokens = candidate.split("[\\s_-]+");

        int matches = 0;
        for (String targetToken : targetTokens) {
            for (String candidateToken : candidateTokens) {
                if (targetToken.equals(candidateToken) ||
                    targetToken.contains(candidateToken) ||
                    candidateToken.contains(targetToken)) {
                    matches++;
                    break;
                }
            }
        }

        return (double) matches / Math.max(targetTokens.length, candidateTokens.length);
    }


    private double calculateAffixSimilarity(String target, String candidate) {
        int commonPrefix = 0;
        int minLength = Math.min(target.length(), candidate.length());

        for (int i = 0; i < minLength; i++) {
            if (target.charAt(i) == candidate.charAt(i)) {
                commonPrefix++;
            } else {
                break;
            }
        }

        int commonSuffix = 0;
        for (int i = 1; i <= minLength - commonPrefix; i++) {
            if (target.charAt(target.length() - i) == candidate.charAt(candidate.length() - i)) {
                commonSuffix++;
            } else {
                break;
            }
        }

        return (double) (commonPrefix + commonSuffix) / Math.max(target.length(), candidate.length());
    }


    private double calculatePathStructuralSimilarity(String[] targetParts, String[] candidateParts) {
        if (targetParts.length != candidateParts.length) {
            return 0.0; // Different structure
        }

        int matches = 0;
        for (int i = 0; i < targetParts.length; i++) {
            String targetPart = normalizeArrayIndices(targetParts[i]);
            String candidatePart = normalizeArrayIndices(candidateParts[i]);

            if (targetPart.equals(candidatePart)) {
                matches++;
            } else if (targetPart.contains(candidatePart) || candidatePart.contains(targetPart)) {
                matches += 0.5; // Partial match
            }
        }

        return (double) matches / targetParts.length;
    }


    private String normalizeArrayIndices(String path) {
        return path.replaceAll("\\[\\d+\\]", "[*]");
    }

    /**
     * Check if a unit has a property using wildcard paths like "ModulesDescriptors[*].TagSet"
     */
    private boolean hasPropertyWithWildcards(ObjectValue unit, String propertyPath) {
        // If no wildcards, use regular property checking with file type
        NDFValue.NDFFileType fileType = parentWindow.getCurrentFileType();
        if (!propertyPath.contains("[*]")) {
            return PropertyUpdater.hasProperty(unit, propertyPath, fileType);
        }

        // Split on [*] to get the parts
        String[] mainParts = propertyPath.split("\\[\\*\\]");
        if (mainParts.length < 2) {
            return false; // Invalid format
        }

        String arrayPropertyName = mainParts[0]; // "ModulesDescriptors"
        String remainingPath = mainParts[1]; // ".TagSet" or ".BlindageProperties.ExplosiveReactiveArmor"
        if (remainingPath.startsWith(".")) {
            remainingPath = remainingPath.substring(1);
        }

        NDFValue arrayValue = unit.getProperty(arrayPropertyName);
        if (!(arrayValue instanceof NDFValue.ArrayValue)) {
            return false; // Not an array
        }

        NDFValue.ArrayValue array = (NDFValue.ArrayValue) arrayValue;
        for (int i = 0; i < array.getElements().size(); i++) {
            NDFValue element = array.getElements().get(i);
            if (element instanceof NDFValue.ObjectValue) {
                NDFValue.ObjectValue elementObj = (NDFValue.ObjectValue) element;
                if (PropertyUpdater.hasProperty(elementObj, remainingPath, fileType)) {
                    return true; // Found at least one element with this property
                }
            }
        }

        return false; // Not found in any array element
    }


    private String getValueForComparison(NDFValue value) {
        // For string values, extract the raw string content with quote type prefix
        // This ensures consistent comparison and preserves quote type information
        if (value instanceof NDFValue.StringValue) {
            NDFValue.StringValue stringValue = (NDFValue.StringValue) value;
            String prefix = stringValue.useDoubleQuotes() ? "DQ:" : "SQ:";
            return prefix + stringValue.getValue();
        }

        // For other value types, use the standard toString representation
        return value.toString();
    }
}
