package com.warnomodmaker.gui;

import com.warnomodmaker.gui.theme.WarnoTheme;
import com.warnomodmaker.model.CrossSystemIntegrityManager;
import com.warnomodmaker.model.CrossSystemIntegrityManager.CrossSystemValidationResult;

import javax.swing.*;
import java.awt.*;

/**
 * Dialog displaying unified cross-system validation results:
 * broken references, missing files, duplicate GUIDs, and cross-file health status.
 */
public class CrossSystemIntegrityDialog extends JDialog {

    private final CrossSystemIntegrityManager integrityManager;
    private JLabel statusBanner;
    private JTextArea issuesArea;
    private JTextArea unloadedRefsArea;
    private JTextArea summaryArea;

    public CrossSystemIntegrityDialog(JFrame owner, CrossSystemIntegrityManager integrityManager) {
        super(owner, "Cross-System Integrity Checker", true);
        this.integrityManager = integrityManager;

        initComponents();
        runValidation();

        setSize(750, 520);
        setLocationRelativeTo(owner);
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Top Banner
        statusBanner = new JLabel("Checking cross-system integrity...", JLabel.LEFT);
        statusBanner.setFont(statusBanner.getFont().deriveFont(Font.BOLD, 13f));
        statusBanner.setOpaque(true);
        statusBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(WarnoTheme.TACTICAL_GREEN_DARK, 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        mainPanel.add(statusBanner, BorderLayout.NORTH);

        // Center Tabbed Details
        JTabbedPane tabbedPane = new JTabbedPane();

        issuesArea = createReadOnlyTextArea();
        tabbedPane.addTab("Validation Issues", new JScrollPane(issuesArea));

        unloadedRefsArea = createReadOnlyTextArea();
        tabbedPane.addTab("External / Unloaded References", new JScrollPane(unloadedRefsArea));

        summaryArea = createReadOnlyTextArea();
        tabbedPane.addTab("Validation Summary", new JScrollPane(summaryArea));

        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // Bottom Button Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton refreshBtn = new JButton("Re-run Validation");
        refreshBtn.addActionListener(e -> runValidation());

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());

        buttonPanel.add(refreshBtn);
        buttonPanel.add(closeBtn);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private JTextArea createReadOnlyTextArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setBackground(WarnoTheme.TACTICAL_GREEN_DARK);
        area.setForeground(Color.WHITE);
        area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        return area;
    }

    private void runValidation() {
        if (integrityManager == null) {
            statusBanner.setText("Integrity manager is unavailable.");
            statusBanner.setBackground(WarnoTheme.TACTICAL_GREEN_DARK);
            return;
        }

        CrossSystemValidationResult result = integrityManager.validateAllSystems();

        if (!result.hasIssues()) {
            statusBanner.setText("All cross-system references and GUIDs are valid across all loaded files.");
            statusBanner.setBackground(new Color(30, 80, 40));
            statusBanner.setForeground(new Color(150, 255, 150));
        } else {
            statusBanner.setText("Found " + result.getTotalIssueCount() + " issue(s) across loaded files.");
            statusBanner.setBackground(new Color(90, 35, 35));
            statusBanner.setForeground(new Color(255, 160, 160));
        }

        // Issues
        StringBuilder sbIssues = new StringBuilder();
        if (!result.hasIssues()) {
            sbIssues.append("No reference or GUID conflicts detected in currently loaded files.\n\n");
            sbIssues.append("Your modifications maintain 100% cross-file integrity!");
        } else {
            for (String issue : result.getAllIssues()) {
                sbIssues.append(issue).append("\n");
            }
        }
        issuesArea.setText(sbIssues.toString());
        issuesArea.setCaretPosition(0);

        // Summary
        StringBuilder sbSummary = new StringBuilder();
        sbSummary.append("Cross-System Integrity Status\n");
        sbSummary.append("-----------------------------\n");
        sbSummary.append("Total issues in loaded files: ").append(result.getTotalIssueCount()).append("\n");
        sbSummary.append("Broken reference issues: ").append(result.getReferenceResult().getTotalBrokenReferences()).append("\n");
        sbSummary.append("GUID conflicts: ").append(result.getGuidResult().getTotalConflicts()).append("\n");
        sbSummary.append("Orphaned GUIDs: ").append(result.getGuidResult().getTotalOrphanedGuids()).append("\n\n");
        sbSummary.append("Detailed log entries: ").append(result.getAllIssues().size());
        summaryArea.setText(sbSummary.toString());
        summaryArea.setCaretPosition(0);

        // Unloaded references tab
        StringBuilder sbUnloaded = new StringBuilder();
        sbUnloaded.append("The following references point to NDF files that are not currently open in tabs.\n");
        sbUnloaded.append("In WARNO, these reference base game files and are normal when editing standalone files:\n\n");
        boolean hasUnloaded = false;
        for (String issue : result.getAllIssues()) {
            if (issue.contains("UNLOADED FILE REFERENCES")) {
                hasUnloaded = true;
            }
            if (hasUnloaded) {
                sbUnloaded.append(issue).append("\n");
            }
        }
        if (!hasUnloaded) {
            sbUnloaded.append("(No unloaded references detected)");
        }
        unloadedRefsArea.setText(sbUnloaded.toString());
        unloadedRefsArea.setCaretPosition(0);
    }
}
