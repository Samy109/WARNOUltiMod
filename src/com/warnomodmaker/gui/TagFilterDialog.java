package com.warnomodmaker.gui;

import com.warnomodmaker.model.NDFValue.ObjectValue;
import com.warnomodmaker.model.TagExtractor;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * Dialog for filtering units by tags
 */
public class TagFilterDialog extends JDialog {
    private final List<ObjectValue> allUnits;
    private final Map<String, Set<String>> categorizedTags;
    private final Map<String, JPanel> categoryPanels;
    private final Map<String, JCheckBox> tagCheckBoxes;
    private final JRadioButton anyTagsRadio;
    private final JRadioButton allTagsRadio;
    private final JLabel statusLabel;

    private Set<String> selectedTags;
    private boolean confirmed;

    public TagFilterDialog(JFrame parent, List<ObjectValue> units) {
        super(parent, "Filter Units by Tags", true);
        this.allUnits = units;
        this.categorizedTags = TagExtractor.extractAllTags(units);
        this.categoryPanels = new HashMap<>();
        this.tagCheckBoxes = new HashMap<>();
        this.selectedTags = new HashSet<>();
        this.confirmed = false;

        // Create radio buttons for filter mode
        this.anyTagsRadio = new JRadioButton("Units with ANY selected tags", true);
        this.allTagsRadio = new JRadioButton("Units with ALL selected tags", false);
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(anyTagsRadio);
        modeGroup.add(allTagsRadio);

        this.statusLabel = new JLabel("Select tags to filter units");

        initializeGUI();
        updateStatus();
    }

    private void initializeGUI() {
        setSize(600, 500);
        setLocationRelativeTo(getParent());
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top panel with mode selection
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("Filter Mode:"));
        topPanel.add(anyTagsRadio);
        topPanel.add(allTagsRadio);

        // Add listeners to radio buttons
        anyTagsRadio.addActionListener(e -> updateStatus());
        allTagsRadio.addActionListener(e -> updateStatus());

        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Center panel with tag categories
        JPanel centerPanel = new JPanel(new BorderLayout());
        JScrollPane scrollPane = new JScrollPane(createTagsPanel());
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // Bottom panel with status and buttons
        JPanel bottomPanel = new JPanel(new BorderLayout());

        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        bottomPanel.add(statusLabel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout());

        JButton selectAllButton = new JButton("Select All");
        selectAllButton.addActionListener(this::selectAllTags);
        buttonPanel.add(selectAllButton);

        JButton clearAllButton = new JButton("Clear All");
        clearAllButton.addActionListener(this::clearAllTags);
        buttonPanel.add(clearAllButton);

        JButton okButton = new JButton("OK");
        okButton.addActionListener(this::confirmSelection);
        buttonPanel.add(okButton);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(cancelButton);

        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private JPanel createTagsPanel() {
        JPanel tagsPanel = new JPanel();
        tagsPanel.setLayout(new BoxLayout(tagsPanel, BoxLayout.Y_AXIS));

        for (Map.Entry<String, Set<String>> entry : categorizedTags.entrySet()) {
            String category = entry.getKey();
            Set<String> tags = entry.getValue();

            if (!tags.isEmpty()) {
                JPanel categoryPanel = createCategoryPanel(category, tags);
                categoryPanels.put(category, categoryPanel);
                tagsPanel.add(categoryPanel);
                tagsPanel.add(Box.createVerticalStrut(5));
            }
        }

        return tagsPanel;
    }

    private JPanel createCategoryPanel(String category, Set<String> tags) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new TitledBorder(category));

        // Sort tags alphabetically
        List<String> sortedTags = new ArrayList<>(tags);
        sortedTags.sort(String.CASE_INSENSITIVE_ORDER);

        for (String tag : sortedTags) {
            JCheckBox checkBox = new JCheckBox(tag);
            checkBox.addActionListener(this::tagSelectionChanged);
            checkBox.addItemListener(e -> {
                updateSelectedTags();
                updateStatus();
            });
            tagCheckBoxes.put(tag, checkBox);
            panel.add(checkBox);
        }

        return panel;
    }

    private void tagSelectionChanged(ActionEvent e) {
        updateSelectedTags();
        updateStatus();
    }

    private void updateSelectedTags() {
        selectedTags.clear();
        for (Map.Entry<String, JCheckBox> entry : tagCheckBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selectedTags.add(entry.getKey());
            }
        }
    }

    private void updateStatus() {
        updateSelectedTags();

        if (selectedTags.isEmpty()) {
            statusLabel.setText("Select tags to filter units - All " + allUnits.size() + " units will be included");
            return;
        }

        List<ObjectValue> filteredUnits;
        if (anyTagsRadio.isSelected()) {
            filteredUnits = TagExtractor.getUnitsWithTags(allUnits, selectedTags);
        } else {
            filteredUnits = TagExtractor.getUnitsWithAllTags(allUnits, selectedTags);
        }

        String mode = anyTagsRadio.isSelected() ? "ANY" : "ALL";
        statusLabel.setText(String.format("Found %d units with %s of the selected tags (%d total units)",
                                        filteredUnits.size(), mode, allUnits.size()));
    }

    private void selectAllTags(ActionEvent e) {
        for (JCheckBox checkBox : tagCheckBoxes.values()) {
            checkBox.setSelected(true);
        }
        updateStatus();
    }

    private void clearAllTags(ActionEvent e) {
        for (JCheckBox checkBox : tagCheckBoxes.values()) {
            checkBox.setSelected(false);
        }
        updateStatus();
    }

    private void confirmSelection(ActionEvent e) {
        updateSelectedTags();
        confirmed = true;
        dispose();
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public Set<String> getSelectedTags() {
        return new HashSet<>(selectedTags);
    }

    public boolean isAnyTagsMode() {
        return anyTagsRadio.isSelected();
    }

    /**
     * Gets the filtered units based on current selection
     */
    public List<ObjectValue> getFilteredUnits() {
        if (selectedTags.isEmpty()) {
            return new ArrayList<>(allUnits);
        }

        if (anyTagsRadio.isSelected()) {
            return TagExtractor.getUnitsWithTags(allUnits, selectedTags);
        } else {
            return TagExtractor.getUnitsWithAllTags(allUnits, selectedTags);
        }
    }
}
