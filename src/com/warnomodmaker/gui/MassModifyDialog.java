package com.warnomodmaker.gui;

import com.warnomodmaker.model.NDFValue;
import com.warnomodmaker.model.NDFValue.*;
import com.warnomodmaker.model.PropertyScanner;
import com.warnomodmaker.model.PropertyUpdater;
import com.warnomodmaker.model.ModificationTracker;
import com.warnomodmaker.model.ModuleResolver;
import com.warnomodmaker.model.TagExtractor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

public class MassModifyDialog extends JDialog {
    private List<ObjectValue> unitDescriptors;
    private ModificationTracker modificationTracker;
    private boolean modified;
    private PropertyScanner propertyScanner;
    private List<PropertyScanner.PropertyInfo> favoriteProperties;
    private NDFFileType fileType;
    private JComboBox<String> categoryComboBox;
    private JComboBox<PropertyScanner.PropertyInfo> propertyComboBox;
    private JTextField propertyPathField;
    private JComboBox<PropertyUpdater.ModificationType> modificationTypeComboBox;
    private JTextField valueField;
    private JCheckBox filterUnitsCheckBox;
    private JTextField filterField;
    private JCheckBox tagFilterCheckBox;
    private JButton tagFilterButton;
    private JLabel statusLabel;

    // Tag filtering state
    private Set<String> selectedTags;
    private boolean useAnyTagsMode;
    private JButton applyButton;
    private JButton cancelButton;
    private JButton searchButton;
    private JButton refreshButton;
    private static final String CATEGORY_FAVORITES = "Favorites";
    private static final String CATEGORY_CUSTOM = "Custom Property";


    public MassModifyDialog(JFrame parent, List<ObjectValue> unitDescriptors) {
        this(parent, unitDescriptors, null, NDFFileType.UNKNOWN);
    }


    public MassModifyDialog(JFrame parent, List<ObjectValue> unitDescriptors, NDFFileType fileType) {
        this(parent, unitDescriptors, null, fileType);
    }


    public MassModifyDialog(JFrame parent, List<ObjectValue> unitDescriptors, ModificationTracker modificationTracker) {
        this(parent, unitDescriptors, modificationTracker, NDFFileType.UNKNOWN);
    }


    public MassModifyDialog(JFrame parent, List<ObjectValue> unitDescriptors, ModificationTracker modificationTracker, NDFFileType fileType) {
        super(parent, "Mass Modify - Dynamic Property Discovery", true);

        this.unitDescriptors = unitDescriptors;
        this.modificationTracker = modificationTracker;
        this.fileType = fileType;
        this.modified = false;

        // Set file type context in PropertyUpdater for proper navigation
        PropertyUpdater.setFileType(fileType);
        this.favoriteProperties = new ArrayList<>();
        this.selectedTags = new HashSet<>();
        this.useAnyTagsMode = true;
        this.propertyScanner = new PropertyScanner(unitDescriptors, fileType);
        SwingUtilities.invokeLater(() -> {
            JDialog progressDialog = new JDialog(this, "Scanning Properties", true);
            JProgressBar progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);
            progressBar.setString("Discovering properties from " + unitDescriptors.size() + " units...");
            progressBar.setStringPainted(true);

            progressDialog.add(progressBar);
            progressDialog.setSize(400, 100);
            progressDialog.setLocationRelativeTo(this);
            SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    propertyScanner.scanProperties();
                    return null;
                }

                @Override
                protected void done() {
                    progressDialog.dispose();
                    initializeGUI();
                    setVisible(true);
                }
            };

            worker.execute();
            progressDialog.setVisible(true);
        });
    }


    private void initializeGUI() {
        setSize(650, 400);
        setLocationRelativeTo(getParent());
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Category
        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(new JLabel("Category:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        Map<String, List<PropertyScanner.PropertyInfo>> categories = propertyScanner.getCategorizedProperties();
        String[] categoryNames = new String[categories.size() + 1];
        categoryNames[0] = CATEGORY_CUSTOM;
        int i = 1;
        for (String category : categories.keySet()) {
            categoryNames[i++] = category;
        }

        categoryComboBox = new JComboBox<>(categoryNames);
        categoryComboBox.addActionListener(this::categoryChanged);
        formPanel.add(categoryComboBox, gbc);

        // Refresh button
        gbc.gridx = 2;
        gbc.weightx = 0.0;
        refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(this::refreshProperties);
        refreshButton.setToolTipText("Re-scan properties from loaded units");
        formPanel.add(refreshButton, gbc);

        // Property
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel("Property:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        propertyComboBox = new JComboBox<>();
        propertyComboBox.addActionListener(this::propertyChanged);
        formPanel.add(propertyComboBox, gbc);

        // Property path with help
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        JLabel pathLabel = new JLabel("Property Path:");
        pathLabel.setToolTipText("Enter the full path to the property you want to modify");
        formPanel.add(pathLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        propertyPathField = new JTextField();
        propertyPathField.setEnabled(false);
        propertyPathField.setToolTipText("Examples: MaxPhysicalDamages, ModulesDescriptors[5].BlindageProperties.ArmorThickness");
        formPanel.add(propertyPathField, gbc);

        // Help and Search buttons
        gbc.gridx = 2;
        gbc.weightx = 0.0;
        JPanel pathButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));

        JButton helpButton = new JButton("Help");
        helpButton.setPreferredSize(new Dimension(60, 25));
        helpButton.addActionListener(this::showPropertyPathHelp);
        helpButton.setToolTipText("Show property path format help");
        pathButtonPanel.add(helpButton);

        searchButton = new JButton("Search");
        searchButton.addActionListener(this::searchProperties);
        searchButton.setToolTipText("Search for properties by name or path");
        pathButtonPanel.add(searchButton);

        formPanel.add(pathButtonPanel, gbc);

        // Modification type
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel("Modification:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        modificationTypeComboBox = new JComboBox<>(PropertyUpdater.ModificationType.values());
        formPanel.add(modificationTypeComboBox, gbc);

        // Value
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel("Value:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        valueField = new JTextField();
        valueField.setToolTipText("Enter value. For arrays: 'Tag1,Tag2' to add, '-Tag1,-Tag2' to remove, 'Tag1,-Tag2,Tag3' for mixed operations");
        formPanel.add(valueField, gbc);

        // Filter units checkbox
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 0.0;
        filterUnitsCheckBox = new JCheckBox("Filter units:");
        filterUnitsCheckBox.addActionListener(e -> {
            filterField.setEnabled(filterUnitsCheckBox.isSelected());
            updateStatusLabel();
        });
        formPanel.add(filterUnitsCheckBox, gbc);

        // Filter field
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        filterField = new JTextField();
        filterField.setEnabled(false);
        filterField.setToolTipText("Enter text to filter units by name (e.g., 'Tank' for units containing 'Tank')");
        formPanel.add(filterField, gbc);

        // Tag filter checkbox
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.weightx = 0.0;
        tagFilterCheckBox = new JCheckBox("Filter by tags:");
        tagFilterCheckBox.addActionListener(e -> {
            tagFilterButton.setEnabled(tagFilterCheckBox.isSelected());
            updateStatusLabel();
        });
        formPanel.add(tagFilterCheckBox, gbc);

        // Tag filter button
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        tagFilterButton = new JButton("Select Tags...");
        tagFilterButton.setEnabled(false);
        tagFilterButton.addActionListener(this::showTagFilterDialog);
        tagFilterButton.setToolTipText("Filter units by their tags (e.g., recon tanks, AT vehicles)");
        formPanel.add(tagFilterButton, gbc);

        // Status label
        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        statusLabel = new JLabel();
        updateStatusLabel();
        formPanel.add(statusLabel, gbc);

        mainPanel.add(formPanel, BorderLayout.CENTER);
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton debugButton = new JButton("Debug Info");
        debugButton.addActionListener(this::showDebugInfo);
        buttonPanel.add(debugButton);

        applyButton = new JButton("Apply Changes");
        applyButton.addActionListener(this::applyModification);
        buttonPanel.add(applyButton);

        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(cancelButton);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        categoryChanged(null);
    }


    public boolean isModified() {
        return modified;
    }


    private void categoryChanged(ActionEvent e) {
        propertyComboBox.removeAllItems();
        String category = (String) categoryComboBox.getSelectedItem();

        if (CATEGORY_CUSTOM.equals(category)) {
            propertyPathField.setEnabled(true);
            propertyPathField.setText("");
            propertyPathField.addActionListener(evt -> updateStatusLabel());
            propertyPathField.addCaretListener(evt -> updateStatusLabel());
        } else {
            propertyPathField.setEnabled(false);
            Map<String, List<PropertyScanner.PropertyInfo>> categories = propertyScanner.getCategorizedProperties();
            List<PropertyScanner.PropertyInfo> properties = categories.get(category);
            if (properties != null) {
                for (PropertyScanner.PropertyInfo property : properties) {
                    propertyComboBox.addItem(property);
                }
            }
        }

        // Select the first property if available
        if (propertyComboBox.getItemCount() > 0) {
            propertyComboBox.setSelectedIndex(0);
        }

        updateStatusLabel();
    }


    private void propertyChanged(ActionEvent e) {
        String category = (String) categoryComboBox.getSelectedItem();

        if (!CATEGORY_CUSTOM.equals(category) && propertyComboBox.getSelectedItem() != null) {
            PropertyScanner.PropertyInfo property = (PropertyScanner.PropertyInfo) propertyComboBox.getSelectedItem();
            propertyPathField.setText(property.path);
        }

        updateStatusLabel();
    }


    private void showTagFilterDialog(ActionEvent e) {
        TagFilterDialog tagDialog = new TagFilterDialog(
            (JFrame) SwingUtilities.getWindowAncestor(this),
            unitDescriptors
        );

        // Pre-select current tags if any
        if (!selectedTags.isEmpty()) {
            // The dialog doesn't support pre-selection yet, but we could add it
        }

        tagDialog.setVisible(true);

        if (tagDialog.isConfirmed()) {
            selectedTags = tagDialog.getSelectedTags();
            useAnyTagsMode = tagDialog.isAnyTagsMode();
            if (selectedTags.isEmpty()) {
                tagFilterButton.setText("Select Tags...");
            } else {
                String mode = useAnyTagsMode ? "ANY" : "ALL";
                tagFilterButton.setText(String.format("Tags (%s of %d)", mode, selectedTags.size()));
            }

            updateStatusLabel();
        }
    }


    private void updateStatusLabel() {
        String propertyPath = propertyPathField.getText();

        if (propertyPath.isEmpty()) {
            String category = (String) categoryComboBox.getSelectedItem();
            if (CATEGORY_CUSTOM.equals(category)) {
                statusLabel.setText("Enter a custom property path to see affected units (click Help for format guide)");
            } else {
                statusLabel.setText("Select a property to see affected units");
            }
            return;
        }

        // Start with all units, then apply filters
        List<ObjectValue> workingUnits = new ArrayList<>(unitDescriptors);

        // Apply tag filter first if enabled
        if (tagFilterCheckBox.isSelected() && !selectedTags.isEmpty()) {
            if (useAnyTagsMode) {
                workingUnits = TagExtractor.getUnitsWithTags(workingUnits, selectedTags);
            } else {
                workingUnits = TagExtractor.getUnitsWithAllTags(workingUnits, selectedTags);
            }
        }

        // Count units that have this property - direct checking only
        int totalUnits = unitDescriptors.size();
        int tagFilteredUnits = workingUnits.size();
        int unitsWithProperty = 0;
        for (ObjectValue unit : workingUnits) {
            if (hasPropertyDirect(unit, propertyPath)) {
                unitsWithProperty++;
            }
        }

        // Apply name filter if enabled
        int filteredUnits = unitsWithProperty;
        if (filterUnitsCheckBox.isSelected() && !filterField.getText().isEmpty()) {
            String filter = filterField.getText().toLowerCase();
            filteredUnits = 0;
            for (ObjectValue unit : workingUnits) {
                if (hasPropertyDirect(unit, propertyPath)) {
                    String unitName = unit.getInstanceName();
                    if (unitName != null && unitName.toLowerCase().contains(filter)) {
                        filteredUnits++;
                    }
                }
            }
        }

        // Build status message
        StringBuilder statusText = new StringBuilder();
        if (tagFilterCheckBox.isSelected() && !selectedTags.isEmpty()) {
            String mode = useAnyTagsMode ? "ANY" : "ALL";
            statusText.append(String.format("Tag filter (%s): %d/%d units. ", mode, tagFilteredUnits, totalUnits));
        }
        statusText.append(String.format("Property found in %d units. %d units will be modified.",
                                      unitsWithProperty, filteredUnits));

        statusLabel.setText(statusText.toString());
    }


    private void refreshProperties(ActionEvent e) {
        JDialog progressDialog = new JDialog(this, "Refreshing Properties", true);
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setString("Re-scanning properties...");
        progressBar.setStringPainted(true);

        progressDialog.add(progressBar);
        progressDialog.setSize(300, 100);
        progressDialog.setLocationRelativeTo(this);

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                propertyScanner.scanProperties();
                return null;
            }

            @Override
            protected void done() {
                progressDialog.dispose();

                // Refresh category combo box
                String selectedCategory = (String) categoryComboBox.getSelectedItem();
                categoryComboBox.removeAllItems();

                categoryComboBox.addItem(CATEGORY_CUSTOM);

                Map<String, List<PropertyScanner.PropertyInfo>> categories = propertyScanner.getCategorizedProperties();
                for (String category : categories.keySet()) {
                    categoryComboBox.addItem(category);
                }

                // Restore selection
                if (selectedCategory != null) {
                    categoryComboBox.setSelectedItem(selectedCategory);
                }

                categoryChanged(null);
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }


    private void showPropertyPathHelp(ActionEvent e) {
        String helpText = "<html><body style='width: 400px;'>" +
            "<h3>Property Path Format Guide</h3>" +
            "<p>Property paths specify exactly which property to modify. Here are the formats:</p>" +

            "<h4>Simple Properties:</h4>" +
            "<ul>" +
            "<li><b>MaxPhysicalDamages</b> - Direct property</li>" +
            "<li><b>ClassNameForDebug</b> - String property</li>" +
            "<li><b>DescriptorId</b> - GUID property</li>" +
            "</ul>" +

            "<h4>Module Properties (with specific index):</h4>" +
            "<ul>" +
            "<li><b>ModulesDescriptors[5].BlindageProperties.ArmorThickness</b></li>" +
            "<li><b>ModulesDescriptors[12].MaxSpeed</b></li>" +
            "<li><b>ModulesDescriptors[3].WeaponDescriptor.Ammunition</b></li>" +
            "</ul>" +

            "<h4>Wildcard Properties (all matching modules):</h4>" +
            "<ul>" +
            "<li><b>ModulesDescriptors[*].BlindageProperties.ArmorThickness</b></li>" +
            "<li><b>ModulesDescriptors[*].MaxSpeed</b></li>" +
            "</ul>" +

            "<h4>Array Properties (Tags, Lists):</h4>" +
            "<ul>" +
            "<li><b>ModulesDescriptors[*].TagSet</b> - Unit tags</li>" +
            "<li><b>ModulesDescriptors[*].SearchedTagsInEngagementTarget</b> - Target tags</li>" +
            "</ul>" +

            "<h4>Template Reference Properties:</h4>" +
            "<ul>" +
            "<li><b>ModulesDescriptors[*].ExperienceLevelsPackDescriptor</b> - Experience pack reference</li>" +
            "<li><b>ModulesDescriptors[*].WeaponDescriptor</b> - Weapon reference</li>" +
            "<li><b>ModulesDescriptors[*].Ammunition</b> - Ammunition reference</li>" +
            "</ul>" +

            "<h4>Template Reference Values:</h4>" +
            "<ul>" +
            "<li><b>With ~/:</b> ~/ExperienceLevelsPackDescriptor_XP_pack_AA_v3</li>" +
            "<li><b>Without ~/:</b> ExperienceLevelsPackDescriptor_XP_pack_AA_v3</li>" +
            "<li><b>Note:</b> Template references can only be set, not modified mathematically</li>" +
            "</ul>" +

            "<h4>Array Editing Syntax:</h4>" +
            "<ul>" +
            "<li><b>Add tags:</b> NewTag,AnotherTag,CustomTag</li>" +
            "<li><b>Remove tags:</b> -OldTag,-UnwantedTag</li>" +
            "<li><b>Mixed:</b> NewTag,-OldTag,AnotherTag</li>" +
            "<li><b>String arrays:</b> Replace with single value</li>" +
            "<li><b>Number arrays:</b> Apply modification to all elements</li>" +
            "</ul>" +

            "<h4>Tips:</h4>" +
            "<ul>" +
            "<li>Use the <b>Search</b> button to find properties by name</li>" +
            "<li>Browse categories to see available properties</li>" +
            "<li>Use <b>Debug Info</b> to see the structure of a unit</li>" +
            "<li>Array indices [0], [1], [2] refer to specific modules</li>" +
            "<li>Use [*] to modify the same property in all modules</li>" +
            "<li>For TagSet: prefix with '-' to remove tags, no prefix to add</li>" +
            "</ul>" +
            "</body></html>";

        JOptionPane.showMessageDialog(
            this,
            helpText,
            "Property Path Help",
            JOptionPane.INFORMATION_MESSAGE
        );
    }


    private void searchProperties(ActionEvent e) {
        String searchTerm = JOptionPane.showInputDialog(
            this,
            "Enter search term (property name, path, or description):",
            "Search Properties",
            JOptionPane.QUESTION_MESSAGE
        );

        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            List<PropertyScanner.PropertyInfo> results = propertyScanner.searchProperties(searchTerm.trim());

            if (!results.isEmpty()) {
                JDialog searchResultsDialog = new JDialog(this, "Search Results", true);
                searchResultsDialog.setSize(600, 400);
                searchResultsDialog.setLocationRelativeTo(this);
                DefaultListModel<PropertyScanner.PropertyInfo> listModel = new DefaultListModel<>();
                for (PropertyScanner.PropertyInfo result : results) {
                    listModel.addElement(result);
                }

                JList<PropertyScanner.PropertyInfo> resultsList = new JList<>(listModel);
                resultsList.setCellRenderer(new DefaultListCellRenderer() {
                    @Override
                    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                boolean isSelected, boolean cellHasFocus) {
                        Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                        PropertyScanner.PropertyInfo property = (PropertyScanner.PropertyInfo) value;
                        setText(String.format("<html><b>%s</b><br><i>%s</i><br><small>%s</small></html>",
                                            property.name, property.path, property.description));
                        return component;
                    }
                });
                JButton selectButton = new JButton("Select Property");
                selectButton.addActionListener(event -> {
                    PropertyScanner.PropertyInfo selectedProperty = resultsList.getSelectedValue();
                    if (selectedProperty != null) {
                        String targetCategory = selectedProperty.category;
                        categoryComboBox.setSelectedItem(targetCategory);

                        // Wait for category change to complete, then select property
                        SwingUtilities.invokeLater(() -> {
                            propertyComboBox.setSelectedItem(selectedProperty);
                        });

                        searchResultsDialog.dispose();
                    }
                });
                JPanel panel = new JPanel(new BorderLayout());
                panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

                JLabel titleLabel = new JLabel("Found " + results.size() + " properties matching '" + searchTerm + "':");
                panel.add(titleLabel, BorderLayout.NORTH);

                panel.add(new JScrollPane(resultsList), BorderLayout.CENTER);
                panel.add(selectButton, BorderLayout.SOUTH);

                searchResultsDialog.add(panel);
                searchResultsDialog.setVisible(true);
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    "No properties found matching '" + searchTerm + "'.",
                    "No Results",
                    JOptionPane.INFORMATION_MESSAGE
                );
            }
        }
    }


    private void applyModification(ActionEvent e) {
        String propertyPath = propertyPathField.getText().trim();

        if (propertyPath.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "Please enter a property path.",
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        String valueText = valueField.getText().trim();

        if (valueText.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "Please enter a value.",
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        try {
            double value;
            try {
                value = Double.parseDouble(valueText);
            } catch (NumberFormatException nfe) {
                // For non-numeric input, use a default value and let type-specific handlers deal with it
                // For booleans: "true"/"false" -> 1/0, for strings: use the text directly
                if (valueText.equalsIgnoreCase("true") || valueText.equalsIgnoreCase("yes")) {
                    value = 1.0;
                } else if (valueText.equalsIgnoreCase("false") || valueText.equalsIgnoreCase("no")) {
                    value = 0.0;
                } else {
                    // For string properties, we'll convert the text to a hash code as a number
                    // This is a fallback - the string update method will use the original text
                    value = valueText.hashCode();
                }
            }
            PropertyUpdater.ModificationType modificationType =
                (PropertyUpdater.ModificationType) modificationTypeComboBox.getSelectedItem();
            String filter = null;
            if (filterUnitsCheckBox.isSelected() && !filterField.getText().trim().isEmpty()) {
                filter = filterField.getText().trim().toLowerCase();
            }
            int unitsToModify = countUnitsToModify(propertyPath, filter);
            if (unitsToModify == 0) {
                JOptionPane.showMessageDialog(
                    this,
                    "No units will be modified. The property path may be invalid or no units matched the filter.",
                    "No Units to Modify",
                    JOptionPane.WARNING_MESSAGE
                );
                return;
            }

            int confirm = JOptionPane.showConfirmDialog(
                this,
                String.format("This will modify %d units.\n\nProperty: %s\nModification: %s\nValue: %s\n\nContinue?",
                            unitsToModify, propertyPath, modificationType.getDisplayName(), valueText),
                "Confirm Mass Modification",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );

            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }

            // Apply the modification using PropertyUpdater (same as single updates!)
            int modifiedCount = applyModificationToUnits(propertyPath, modificationType, value, valueText, filter);

            if (modifiedCount > 0) {
                modified = true;

                JOptionPane.showMessageDialog(
                    this,
                    String.format("Successfully modified %d units.\n\nProperty: %s\nModification: %s\nValue: %s",
                                modifiedCount, propertyPath, modificationType.getDisplayName(), valueText),
                    "Modification Applied",
                    JOptionPane.INFORMATION_MESSAGE
                );

                // Clear the value field to prepare for next modification
                valueField.setText("");
                updateStatusLabel();
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    "No units were modified. The property may not exist in the selected units.",
                    "No Units Modified",
                    JOptionPane.WARNING_MESSAGE
                );
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(
                this,
                "Please enter a valid number for the value.",
                "Invalid Number",
                JOptionPane.ERROR_MESSAGE
            );
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                this,
                "Error applying modification: " + ex.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }


    private int countUnitsToModify(String propertyPath, String filter) {
        // Start with all units, then apply filters
        List<ObjectValue> workingUnits = new ArrayList<>(unitDescriptors);

        // Apply tag filter first if enabled
        if (tagFilterCheckBox.isSelected() && !selectedTags.isEmpty()) {
            if (useAnyTagsMode) {
                workingUnits = TagExtractor.getUnitsWithTags(workingUnits, selectedTags);
            } else {
                workingUnits = TagExtractor.getUnitsWithAllTags(workingUnits, selectedTags);
            }
        }

        int count = 0;
        for (ObjectValue unit : workingUnits) {
            if (!hasPropertyDirect(unit, propertyPath)) {
                continue;
            }

            // Apply name filter if specified
            if (filter != null) {
                String unitName = unit.getInstanceName();
                if (unitName == null || !unitName.toLowerCase().contains(filter)) {
                    continue;
                }
            }

            count++;
        }
        return count;
    }


    private boolean hasPropertyDirect(ObjectValue unit, String propertyPath) {
        // Wildcard paths: check if ANY array element has the property
        if (propertyPath.contains("[*]")) {
            return hasPropertyWithWildcards(unit, propertyPath);
        }

        // Regular paths: check if property exists
        if (!PropertyUpdater.hasProperty(unit, propertyPath)) {
            return false;
        }
        NDFValue value = PropertyUpdater.getPropertyValue(unit, propertyPath);
        if (value == null) {
            return false;
        }

        // Apply the same comprehensive filtering as PropertyScanner
        // Skip module type checking for core structural properties
        String lowerPath = propertyPath.toLowerCase();
        if (lowerPath.equals("modulesdescriptors") || lowerPath.matches("modulesdescriptors\\[\\d+\\]")) {
            return isModifiableProperty(value, propertyPath); // Skip module type check for core structural access
        }

        return isModifiableProperty(value, propertyPath) && hasRequiredModuleType(unit, propertyPath);
    }


    private boolean isModifiableProperty(NDFValue value, String propertyPath) {
        System.out.println("DEBUG: isModifiableProperty called with path: " + propertyPath + ", value type: " + value.getType());

        // 1. BOOLEAN PROPERTIES: Only count if True
        if (value.getType() == NDFValue.ValueType.BOOLEAN) {
            BooleanValue boolValue = (BooleanValue) value;
            return boolValue.getValue();
        }

        // 2. TEMPLATE REFERENCES: Allow for "Set to value" operations
        if (value.getType() == NDFValue.ValueType.TEMPLATE_REF ||
            value.getType() == NDFValue.ValueType.RESOURCE_REF) {
            return true; // Template references can be replaced with new values
        }

        // 3. STRING PROPERTIES: Exclude template references and system paths
        if (value.getType() == NDFValue.ValueType.STRING) {
            StringValue stringValue = (StringValue) value;
            String str = stringValue.getValue();

            if (str.startsWith("~/") || str.startsWith("$/") ||
                str.startsWith("GUID:") || str.contains("Texture_") ||
                str.contains("CommonTexture_") || str.contains("Descriptor_")) {
                return false;
            }
            return true;
        }

        // 4. NUMERIC, ENUM: Include
        if (value.getType() == NDFValue.ValueType.NUMBER ||
            value.getType() == NDFValue.ValueType.ENUM) {
            return true;
        }

        // 5. ARRAY PROPERTIES: Allow specific modifiable arrays AND core structural arrays
        if (value.getType() == NDFValue.ValueType.ARRAY) {
            String lowerPath = propertyPath.toLowerCase();
            // Always allow ModulesDescriptors - it's a core structural array that should be accessible
            if (lowerPath.equals("modulesdescriptors")) {
                System.out.println("DEBUG: ModulesDescriptors special case triggered for path: " + propertyPath);
                return true;
            }
            System.out.println("DEBUG: Array path '" + propertyPath + "' (lower: '" + lowerPath + "') not matching 'modulesdescriptors'");
            return isModifiableArray(value, propertyPath);
        }

        // 6. OBJECT PROPERTIES: Allow access to core structural objects and module elements
        if (value.getType() == NDFValue.ValueType.OBJECT) {
            String lowerPath = propertyPath.toLowerCase();
            // Allow access to individual modules within ModulesDescriptors array
            if (lowerPath.matches("modulesdescriptors\\[\\d+\\]")) {
                return true;
            }
            // Generally exclude other complex objects to avoid clutter
            return false;
        }

        // 7. MAP PROPERTIES: Generally exclude to avoid clutter
        if (value.getType() == NDFValue.ValueType.MAP) {
            return false;
        }

        return true;
    }


    private boolean isModifiableArray(NDFValue value, String propertyPath) {
        if (!(value instanceof ArrayValue)) {
            return false;
        }

        ArrayValue arrayValue = (ArrayValue) value;
        String lowerPath = propertyPath.toLowerCase();

        // Core structural arrays that should always be accessible
        if (lowerPath.equals("modulesdescriptors")) {
            return true;
        }

        // SpecialtiesList arrays are modifiable
        if (lowerPath.contains("specialtieslist")) {
            return true;
        }

        // Arrays of simple values (strings, numbers, tuples) that are modifiable
        if (!arrayValue.getElements().isEmpty()) {
            NDFValue firstElement = arrayValue.getElements().get(0);

            // Arrays of strings are often modifiable (like tag lists)
            if (firstElement instanceof StringValue) {
                String str = ((StringValue) firstElement).getValue();
                // Exclude arrays of template references or system identifiers
                if (str.startsWith("~/") || str.startsWith("$/") ||
                    str.startsWith("GUID:") || str.contains("Texture_")) {
                    return false;
                }
                return true; // Arrays of simple strings are modifiable
            }

            // Arrays of numbers are often modifiable (like coordinate lists, value arrays)
            if (firstElement instanceof NumberValue) {
                return true;
            }

            // Arrays of tuples are often modifiable (like BaseHitValueModifiers)
            if (firstElement instanceof TupleValue) {
                return true; // Arrays of tuples (like accuracy modifiers) are modifiable
            }
        }

        return false;
    }


    private boolean hasRequiredModuleType(ObjectValue unit, String propertyPath) {
        // For non-unit descriptor files, skip module type checking
        if (fileType != NDFFileType.UNITE_DESCRIPTOR &&
            fileType != NDFFileType.MISSILE_DESCRIPTORS) {
            return true; // No module restrictions for weapons, ammunition, etc.
        }

        NDFValue modulesValue = unit.getProperty("ModulesDescriptors");
        if (!(modulesValue instanceof ArrayValue)) {
            return true; // If no modules array, allow all properties
        }

        ArrayValue modules = (ArrayValue) modulesValue;
        boolean hasTankFlags = false;
        boolean hasInfantryFlags = false;
        boolean hasHelicopterFlags = false;
        boolean hasPlaneFlags = false;
        boolean hasCanonFlags = false;

        for (NDFValue moduleValue : modules.getElements()) {
            if (moduleValue instanceof ObjectValue) {
                ObjectValue module = (ObjectValue) moduleValue;
                String typeName = module.getTypeName();

                if ("TankFlagsModuleDescriptor".equals(typeName)) {
                    hasTankFlags = true;
                } else if ("InfantryFlagsModuleDescriptor".equals(typeName)) {
                    hasInfantryFlags = true;
                } else if ("HelicoFlagsModuleDescriptor".equals(typeName)) {
                    hasHelicopterFlags = true;
                } else if ("AirplaneFlagsModuleDescriptor".equals(typeName)) {
                    hasPlaneFlags = true;
                } else if ("CanonFlagsModuleDescriptor".equals(typeName)) {
                    hasCanonFlags = true;
                }
            }
        }

        return isPropertyValidForUnitType(propertyPath, hasTankFlags, hasInfantryFlags, hasHelicopterFlags, hasPlaneFlags, hasCanonFlags);
    }


    private boolean isPropertyValidForUnitType(String propertyPath, boolean hasTankFlags,
                                             boolean hasInfantryFlags, boolean hasHelicopterFlags, boolean hasPlaneFlags, boolean hasCanonFlags) {
        String lowerPath = propertyPath.toLowerCase();

        // ARMOR & PROTECTION - All unit types have armor (different families: blindage, infanterie, helico, avion)
        if (lowerPath.contains("blindageproperties") || lowerPath.contains("explosivereactivearmor") ||
            lowerPath.contains("resistance") || lowerPath.contains("armor") ||
            lowerPath.contains("penetration") || lowerPath.contains("protection")) {
            return true; // All unit types have armor properties with different resistance families
        }

        // AIRCRAFT FLIGHT - Only helicopters and planes
        if (lowerPath.contains("upwardspeedinkmph") || lowerPath.contains("torquemanoeuvrability") ||
            lowerPath.contains("cyclicmanoeuvrability") || lowerPath.contains("maxinclination") ||
            lowerPath.contains("gfactorlimit") || lowerPath.contains("rotorarea") ||
            lowerPath.contains("mass") || lowerPath.contains("altitude") ||
            lowerPath.contains("agilityradiusgru") || lowerPath.contains("pitchangle") ||
            lowerPath.contains("rollangle") || lowerPath.contains("rollspeed") ||
            lowerPath.contains("evacangle") || lowerPath.contains("evacuationtime") ||
            lowerPath.contains("travelduration") || lowerPath.contains("flight") ||
            lowerPath.contains("aircraft") || lowerPath.contains("helicopter") ||
            lowerPath.contains("helico")) {
            return hasHelicopterFlags || hasPlaneFlags; // Only aircraft
        }

        // INFANTRY-SPECIFIC - Only infantry units
        if (lowerPath.contains("infantry") || lowerPath.contains("soldier") ||
            lowerPath.contains("infanterie") || lowerPath.contains("crew")) {
            return hasInfantryFlags; // Only infantry units
        }

        // FUEL & LOGISTICS - Mainly for vehicles and aircraft (infantry usually walk)
        if (lowerPath.contains("fuel")) {
            return hasTankFlags || hasHelicopterFlags || hasPlaneFlags || hasCanonFlags; // Vehicles, aircraft, and artillery need fuel
        }

        // TRANSPORT & CAPACITY - Only transport vehicles and helicopters
        if (lowerPath.contains("nbseatsavailable") || lowerPath.contains("loadradiusgru") ||
            lowerPath.contains("transportabletagset") || lowerPath.contains("transporter")) {
            return hasTankFlags || hasHelicopterFlags; // Vehicles and helicopters can transport
        }

        // Movement properties - all units have movement
        if (lowerPath.contains("unitmovingtype") || lowerPath.contains("maxspeedinkmph")) {
            return true; // All unit types have movement
        }

        return true; // Default: allow for all unit types
    }


    private boolean hasPropertyWithWildcards(ObjectValue unit, String propertyPath) {
        // Split on [*] to get the parts
        String[] mainParts = propertyPath.split("\\[\\*\\]");
        if (mainParts.length < 2) {
            return false; // Invalid format
        }

        String arrayPropertyName = mainParts[0]; // "ModulesDescriptors"
        String remainingPath = mainParts[1]; // ".BlindageProperties.ExplosiveReactiveArmor"
        if (remainingPath.startsWith(".")) {
            remainingPath = remainingPath.substring(1);
        }
        NDFValue arrayValue = unit.getProperty(arrayPropertyName);
        if (!(arrayValue instanceof ArrayValue)) {
            return false; // Not an array
        }

        ArrayValue array = (ArrayValue) arrayValue;
        for (int i = 0; i < array.getElements().size(); i++) {
            NDFValue element = array.getElements().get(i);
            if (element instanceof ObjectValue) {
                ObjectValue elementObj = (ObjectValue) element;
                if (PropertyUpdater.hasProperty(elementObj, remainingPath)) {
                    NDFValue value = PropertyUpdater.getPropertyValue(elementObj, remainingPath);
                    if (value != null && isModifiableProperty(value, remainingPath) &&
                        hasRequiredModuleType(unit, propertyPath)) {
                        return true; // Found at least one modifiable property for this unit type
                    }
                }
            }
        }

        return false; // Not found in any array element
    }


    private int applyModificationToUnits(String propertyPath, PropertyUpdater.ModificationType modificationType,
                                       double value, String valueText, String filter) {
        // Start with all units, then apply filters
        List<ObjectValue> workingUnits = new ArrayList<>(unitDescriptors);

        // Apply tag filter first if enabled
        if (tagFilterCheckBox.isSelected() && !selectedTags.isEmpty()) {
            if (useAnyTagsMode) {
                workingUnits = TagExtractor.getUnitsWithTags(workingUnits, selectedTags);
            } else {
                workingUnits = TagExtractor.getUnitsWithAllTags(workingUnits, selectedTags);
            }
        }

        int modifiedCount = 0;

        // Apply the modification to each unit using direct property access
        for (ObjectValue unit : workingUnits) {
            // Apply name filter if specified
            if (filter != null) {
                String unitName = unit.getInstanceName();
                if (unitName == null || !unitName.toLowerCase().contains(filter)) {
                    continue; // Skip this unit
                }
            }

            // Use direct update - same logic as counting phase
            if (updatePropertyDirect(unit, propertyPath, modificationType, value, valueText)) {
                modifiedCount++;
            }
        }

        return modifiedCount;
    }


    private boolean updatePropertyDirect(ObjectValue unit, String propertyPath,
                                      PropertyUpdater.ModificationType modificationType, double value, String valueText) {
        // Wildcard paths: update ALL array elements that have the property
        if (propertyPath.contains("[*]")) {
            return updatePropertyWithWildcards(unit, propertyPath, modificationType, value, valueText);
        }

        // Regular paths: detect type and use appropriate update method
        if (PropertyUpdater.hasProperty(unit, propertyPath)) {
            NDFValue currentValue = PropertyUpdater.getPropertyValue(unit, propertyPath);
            if (currentValue == null) {
                return false;
            }
            switch (currentValue.getType()) {
                case BOOLEAN:
                    boolean boolValue;
                    if (valueText.equalsIgnoreCase("true") || valueText.equalsIgnoreCase("yes") || valueText.equals("1")) {
                        boolValue = true;
                    } else if (valueText.equalsIgnoreCase("false") || valueText.equalsIgnoreCase("no") || valueText.equals("0")) {
                        boolValue = false;
                    } else {
                        // Fallback: use numeric conversion (0 = false, anything else = true)
                        boolValue = value != 0;
                    }
                    return PropertyUpdater.updateBooleanProperty(unit, propertyPath, boolValue, modificationTracker);

                case NUMBER:
                    return PropertyUpdater.updateNumericProperty(unit, propertyPath, modificationType, value, modificationTracker);

                case STRING:
                    return PropertyUpdater.updateStringProperty(unit, propertyPath, valueText, modificationTracker);

                case ENUM:
                case RAW_EXPRESSION:
                    return PropertyUpdater.updateEnumProperty(unit, propertyPath, valueText, modificationTracker);

                case TEMPLATE_REF:
                case RESOURCE_REF:
                    if (modificationType == PropertyUpdater.ModificationType.SET) {
                        return PropertyUpdater.updateTemplateRefProperty(unit, propertyPath, valueText, modificationTracker);
                    } else {
                        return false; // Template references can only be set, not modified mathematically
                    }

                case ARRAY:
                    return updateArrayProperty(unit, propertyPath, modificationType, value, valueText);

                default:
                    // For other types, try numeric update as fallback
                    return PropertyUpdater.updateNumericProperty(unit, propertyPath, modificationType, value, modificationTracker);
            }
        }

        return false; // Property doesn't exist in this unit
    }


    private boolean updateArrayProperty(ObjectValue unit, String propertyPath,
                                      PropertyUpdater.ModificationType modificationType, double value, String valueText) {
        NDFValue currentValue = PropertyUpdater.getPropertyValue(unit, propertyPath);
        if (!(currentValue instanceof ArrayValue)) {
            return false;
        }

        ArrayValue currentArray = (ArrayValue) currentValue;
        String lowerPath = propertyPath.toLowerCase();
        if (lowerPath.contains("tagset")) {
            return updateTagSetArray(unit, propertyPath, currentArray, valueText);
        }
        if (currentArray.getElements().isEmpty()) {
            return false; // Can't modify empty arrays
        }

        // For arrays of simple values, try to add/remove elements
        NDFValue firstElement = currentArray.getElements().get(0);
        if (firstElement instanceof StringValue) {
            return updateStringArray(unit, propertyPath, currentArray, valueText);
        } else if (firstElement instanceof NumberValue) {
            return updateNumberArray(unit, propertyPath, currentArray, modificationType, value);
        }

        return false; // Unsupported array type
    }


    private boolean updateTagSetArray(ObjectValue unit, String propertyPath, ArrayValue currentArray, String valueText) {
        String[] tags = valueText.split(",");
        boolean modified = false;

        for (String tag : tags) {
            tag = tag.trim();
            if (tag.isEmpty()) continue;
            boolean isRemoval = tag.startsWith("-");
            if (isRemoval) {
                tag = tag.substring(1).trim();
            }

            if (isRemoval) {
                for (int i = currentArray.getElements().size() - 1; i >= 0; i--) {
                    NDFValue element = currentArray.getElements().get(i);
                    if (element instanceof StringValue) {
                        String existingTag = ((StringValue) element).getValue();
                        if (tag.equals(existingTag)) {
                            if (currentArray instanceof ArrayValue) {
                                ((ArrayValue) currentArray).remove(i);
                            } else {
                                currentArray.getElements().remove(i);
                            }
                            modified = true;
                            break;
                        }
                    }
                }
            } else {
                boolean exists = false;
                for (NDFValue element : currentArray.getElements()) {
                    if (element instanceof StringValue) {
                        String existingTag = ((StringValue) element).getValue();
                        if (tag.equals(existingTag)) {
                            exists = true;
                            break;
                        }
                    }
                }

                if (!exists) {
                    boolean shouldHaveComma = !currentArray.getElements().isEmpty();
                    if (currentArray instanceof ArrayValue) {
                        ArrayValue arrayVal = (ArrayValue) currentArray;
                        arrayVal.add(NDFValue.createString(tag), shouldHaveComma);
                    } else {
                        currentArray.getElements().add(NDFValue.createString(tag));
                    }
                    modified = true;
                }
            }
        }

        // Record the modification if something changed
        if (modified && modificationTracker != null) {
            String unitName = unit.getInstanceName() != null ? unit.getInstanceName() : "Unknown Unit";
            modificationTracker.recordModification(unitName, propertyPath, currentArray, currentArray);
        }

        return modified;
    }


    private boolean updateStringArray(ObjectValue unit, String propertyPath, ArrayValue currentArray, String valueText) {
        // For string arrays, replace all elements with the new value

        // CRITICAL FIX: Preserve original quote type from first string element if it exists
        boolean useDoubleQuotes = false; // Default to single quotes
        if (!currentArray.getElements().isEmpty()) {
            NDFValue firstElement = currentArray.getElements().get(0);
            if (firstElement instanceof NDFValue.StringValue) {
                NDFValue.StringValue firstString = (NDFValue.StringValue) firstElement;
                useDoubleQuotes = firstString.useDoubleQuotes();
            }
        }

        // CRITICAL FIX: Remove quotes from input if user included them
        String cleanValue = valueText;
        if ((cleanValue.startsWith("\"") && cleanValue.endsWith("\"")) ||
            (cleanValue.startsWith("'") && cleanValue.endsWith("'"))) {
            cleanValue = cleanValue.substring(1, cleanValue.length() - 1);
        }

        currentArray.clear(); // This properly clears both elements and comma tracking
        currentArray.add(NDFValue.createString(cleanValue, useDoubleQuotes));

        if (modificationTracker != null) {
            String unitName = unit.getInstanceName() != null ? unit.getInstanceName() : "Unknown Unit";
            modificationTracker.recordModification(unitName, propertyPath, currentArray, currentArray);
        }

        return true;
    }


    private boolean updateNumberArray(ObjectValue unit, String propertyPath, ArrayValue currentArray,
                                    PropertyUpdater.ModificationType modificationType, double value) {
        // Apply the modification to all numeric elements in the array
        boolean modified = false;

        for (int i = 0; i < currentArray.getElements().size(); i++) {
            NDFValue element = currentArray.getElements().get(i);
            if (element instanceof NumberValue) {
                NumberValue numberValue = (NumberValue) element;
                double currentVal = numberValue.getValue();
                double newVal = calculateNewValue(currentVal, modificationType, value);

                // Round appropriately based on original type
                if (numberValue.wasOriginallyInteger()) {
                    newVal = Math.round(newVal);
                }

                currentArray.getElements().set(i, NDFValue.createNumber(newVal));
                modified = true;
            }
        }

        if (modified && modificationTracker != null) {
            String unitName = unit.getInstanceName() != null ? unit.getInstanceName() : "Unknown Unit";
            modificationTracker.recordModification(unitName, propertyPath, currentArray, currentArray);
        }

        return modified;
    }


    private double calculateNewValue(double currentValue, PropertyUpdater.ModificationType modificationType, double value) {
        switch (modificationType) {
            case SET:
                return value;
            case MULTIPLY:
                return currentValue * value;
            case ADD:
                return currentValue + value;
            case SUBTRACT:
                return currentValue - value;
            case INCREASE_PERCENT:
                return currentValue * (1 + value / 100);
            case DECREASE_PERCENT:
                return currentValue * (1 - value / 100);
            default:
                return currentValue;
        }
    }


    private boolean updatePropertyWithWildcards(ObjectValue unit, String propertyPath,
                                              PropertyUpdater.ModificationType modificationType, double value, String valueText) {
        // Split on [*] to get the parts
        String[] mainParts = propertyPath.split("\\[\\*\\]");
        if (mainParts.length < 2) {
            return false; // Invalid format
        }

        String arrayPropertyName = mainParts[0]; // "ModulesDescriptors"
        String remainingPath = mainParts[1]; // ".BlindageProperties.ExplosiveReactiveArmor"
        if (remainingPath.startsWith(".")) {
            remainingPath = remainingPath.substring(1);
        }
        NDFValue arrayValue = unit.getProperty(arrayPropertyName);
        if (!(arrayValue instanceof ArrayValue)) {
            return false; // Not an array
        }

        ArrayValue array = (ArrayValue) arrayValue;
        boolean modified = false;

        // Try each array element - update ALL that have the property
        for (int i = 0; i < array.getElements().size(); i++) {
            NDFValue element = array.getElements().get(i);
            if (element instanceof ObjectValue) {
                ObjectValue elementObj = (ObjectValue) element;
                if (PropertyUpdater.hasProperty(elementObj, remainingPath)) {
                    // Construct the specific index path for this element
                    String elementPath = arrayPropertyName + "[" + i + "]." + remainingPath;
                    NDFValue currentValue = PropertyUpdater.getPropertyValue(unit, elementPath);
                    if (currentValue != null) {
                        boolean updated = false;
                        switch (currentValue.getType()) {
                            case BOOLEAN:
                                boolean boolValue;
                                if (valueText.equalsIgnoreCase("true") || valueText.equalsIgnoreCase("yes") || valueText.equals("1")) {
                                    boolValue = true;
                                } else if (valueText.equalsIgnoreCase("false") || valueText.equalsIgnoreCase("no") || valueText.equals("0")) {
                                    boolValue = false;
                                } else {
                                    // Fallback: use numeric conversion (0 = false, anything else = true)
                                    boolValue = value != 0;
                                }
                                updated = PropertyUpdater.updateBooleanProperty(unit, elementPath, boolValue, modificationTracker);
                                break;

                            case NUMBER:
                                updated = PropertyUpdater.updateNumericProperty(unit, elementPath, modificationType, value, modificationTracker);
                                break;

                            case STRING:
                                updated = PropertyUpdater.updateStringProperty(unit, elementPath, valueText, modificationTracker);
                                break;

                            case ENUM:
                            case RAW_EXPRESSION:
                                updated = PropertyUpdater.updateEnumProperty(unit, elementPath, valueText, modificationTracker);
                                break;

                            case TEMPLATE_REF:
                            case RESOURCE_REF:
                                if (modificationType == PropertyUpdater.ModificationType.SET) {
                                    updated = PropertyUpdater.updateTemplateRefProperty(unit, elementPath, valueText, modificationTracker);
                                } else {
                                    updated = false; // Template references can only be set, not modified mathematically
                                }
                                break;

                            default:
                                // For other types, try numeric update as fallback
                                updated = PropertyUpdater.updateNumericProperty(unit, elementPath, modificationType, value, modificationTracker);
                                break;
                        }

                        if (updated) {
                            modified = true;
                        }
                    }
                }
            }
        }

        return modified;
    }


    private void showDebugInfo(ActionEvent e) {
        StringBuilder debug = new StringBuilder();

        // Property scanner stats
        debug.append(propertyScanner.getScanningStats()).append("\n\n");

        // Current property path analysis
        String propertyPath = propertyPathField.getText().trim();
        if (!propertyPath.isEmpty()) {
            debug.append("Current Property Path Analysis:\n");
            debug.append("Path: ").append(propertyPath).append("\n");
            debug.append("Contains [*]: ").append(propertyPath.contains("[*]")).append("\n");
            debug.append("Contains []: ").append(propertyPath.contains("[") && propertyPath.contains("]")).append("\n");
            if (propertyPath.contains("[") && propertyPath.contains("]") && !propertyPath.contains("[*]")) {
                String wildcardPath = propertyPath.replaceAll("\\[\\d+\\]", "[*]");
                debug.append("Converted to wildcard: ").append(wildcardPath).append("\n");
            }

            // Test path resolution on first few units
            debug.append("\nPath Resolution Test (first 5 units):\n");
            int testCount = Math.min(5, unitDescriptors.size());
            for (int i = 0; i < testCount; i++) {
                ObjectValue unit = unitDescriptors.get(i);
                String unitName = unit.getInstanceName() != null ? unit.getInstanceName() : "Unit " + i;

                // Test direct resolution (no smart logic)
                boolean hasPropertyDirect = hasPropertyDirect(unit, propertyPath);
                debug.append("  ").append(unitName).append(": ").append(hasPropertyDirect ? "FOUND" : "NOT FOUND").append("\n");
                boolean hasIndexBasedProperty = PropertyUpdater.hasProperty(unit, propertyPath);
                debug.append("    Direct PropertyUpdater: ").append(hasIndexBasedProperty ? "FOUND" : "NOT FOUND").append("\n");
                if (propertyPath.contains("[*]")) {
                    boolean hasWildcardProperty = hasPropertyWithWildcards(unit, propertyPath);
                    debug.append("    Wildcard check: ").append(hasWildcardProperty ? "FOUND" : "NOT FOUND").append("\n");
                }

                // Test if we can actually get the value
                if (hasPropertyDirect) {
                    NDFValue currentValue = PropertyUpdater.getPropertyValue(unit, propertyPath);
                    debug.append("    Current value: ").append(currentValue != null ? currentValue.toString() : "null").append("\n");
                    debug.append("    Value type: ").append(currentValue != null ? currentValue.getType() : "null").append("\n");
                }

                // If it's a wildcard path, show more details
                if (propertyPath.contains("[*]") && hasPropertyDirect) {
                    String[] parts = propertyPath.split("\\[\\*\\]\\.");
                    if (parts.length == 2) {
                        String arrayPropertyName = parts[0];
                        String targetPropertyName = parts[1];
                        NDFValue arrayValue = unit.getProperty(arrayPropertyName);
                        if (arrayValue instanceof ArrayValue) {
                            ArrayValue array = (ArrayValue) arrayValue;
                            debug.append("    Array size: ").append(array.getElements().size()).append("\n");
                            int foundCount = 0;
                            for (int j = 0; j < array.getElements().size(); j++) {
                                NDFValue element = array.getElements().get(j);
                                if (element instanceof ObjectValue) {
                                    ObjectValue elementObj = (ObjectValue) element;
                                    if (elementObj.getProperties().containsKey(targetPropertyName)) {
                                        foundCount++;
                                    }
                                }
                            }
                            debug.append("    Elements with property: ").append(foundCount).append("\n");
                        }
                    }
                }
            }
        }
        JDialog debugDialog = new JDialog(this, "Debug Information", true);
        debugDialog.setSize(800, 600);
        debugDialog.setLocationRelativeTo(this);

        JTextArea textArea = new JTextArea(debug.toString());
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(textArea);
        debugDialog.add(scrollPane);

        debugDialog.setVisible(true);
    }
}
