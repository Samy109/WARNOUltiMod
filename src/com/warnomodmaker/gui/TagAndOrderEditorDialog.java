package com.warnomodmaker.gui;

import com.warnomodmaker.model.NDFValue;
import com.warnomodmaker.model.NDFValue.*;
import com.warnomodmaker.model.PropertyUpdater;
import com.warnomodmaker.model.ModificationTracker;
import com.warnomodmaker.model.TagExtractor;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;

public class TagAndOrderEditorDialog extends JDialog {
    private List<ObjectValue> unitDescriptors; // For bulk tag editing
    private final List<ObjectValue> allUnitDescriptors; // All available units
    private ObjectValue currentUnit; // Currently selected unit for order editing
    private final ModificationTracker modificationTracker;

    // Tag editing components
    private JList<String> currentTagsList;
    private DefaultListModel<String> currentTagsModel;
    private JTextField newTagField;
    private JButton addTagButton;
    private JButton removeTagButton;

    // Order editing components
    private JTextField validOrdersField;
    private JTextField unlockableOrdersField;
    private JButton editValidOrdersButton;
    private JButton editUnlockableOrdersButton;

    // Status and control
    private JLabel statusLabel;
    private JLabel unitsLabel; // Reference to update unit count
    private boolean modified;

    public TagAndOrderEditorDialog(JFrame parent, List<ObjectValue> unitDescriptors, ModificationTracker modificationTracker) {
        super(parent, "Edit Tags and Orders", true);
        this.unitDescriptors = new ArrayList<>(unitDescriptors);
        this.allUnitDescriptors = new ArrayList<>(unitDescriptors);
        this.currentUnit = unitDescriptors.isEmpty() ? null : unitDescriptors.get(0); // Default to first unit
        this.modificationTracker = modificationTracker;
        this.modified = false;

        initializeGUI();
        loadCurrentData();
    }

    private void initializeGUI() {
        setSize(700, 600);
        setLocationRelativeTo(getParent());
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top panel with unit selection info
        JPanel topPanel = createUnitSelectionPanel();
        mainPanel.add(topPanel, BorderLayout.NORTH);
        JTabbedPane tabbedPane = new JTabbedPane();

        // Tags tab
        JPanel tagsPanel = createTagsPanel();
        tabbedPane.addTab("Tags", tagsPanel);

        // Orders tab
        JPanel ordersPanel = createOrdersPanel();
        tabbedPane.addTab("Orders", ordersPanel);

        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // Status and buttons
        JPanel bottomPanel = new JPanel(new BorderLayout());

        statusLabel = new JLabel("Select units to edit their tags and orders");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        bottomPanel.add(statusLabel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout());

        JButton applyButton = new JButton("Apply Changes");
        applyButton.addActionListener(this::applyChanges);
        buttonPanel.add(applyButton);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(cancelButton);

        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private JPanel createUnitSelectionPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 10, 0));
        panel.setBorder(new TitledBorder("Unit Selection"));
        panel.setPreferredSize(new Dimension(0, 80)); // Give more height

        // Tags section (bulk editing)
        JPanel tagsSection = new JPanel(new BorderLayout());
        tagsSection.setBorder(new TitledBorder("Tag Editing (Bulk)"));

        unitsLabel = new JLabel(String.format("Editing tags for: %d units", unitDescriptors.size()));
        unitsLabel.setFont(unitsLabel.getFont().deriveFont(Font.BOLD));
        tagsSection.add(unitsLabel, BorderLayout.NORTH);

        JButton filterButton = new JButton("Filter Units by Tags");
        filterButton.setToolTipText("Select which units to modify using tag filtering");
        filterButton.addActionListener(this::openTagFilterDialog);
        tagsSection.add(filterButton, BorderLayout.CENTER);

        panel.add(tagsSection);

        // Orders section (single unit editing)
        JPanel ordersSection = new JPanel(new BorderLayout());
        ordersSection.setBorder(new TitledBorder("Order Editing (Single Unit)"));

        JLabel currentUnitLabel = new JLabel("Current unit: " +
            (currentUnit != null ? (currentUnit.getInstanceName() != null ? currentUnit.getInstanceName() : "Unknown") : "None"));
        currentUnitLabel.setFont(currentUnitLabel.getFont().deriveFont(Font.BOLD));
        ordersSection.add(currentUnitLabel, BorderLayout.NORTH);

        JButton selectUnitButton = new JButton("Select Unit");
        selectUnitButton.setToolTipText("Choose which unit to edit orders for");
        selectUnitButton.addActionListener(this::openUnitSelectorDialog);
        ordersSection.add(selectUnitButton, BorderLayout.CENTER);

        panel.add(ordersSection);

        return panel;
    }

    private JPanel createTagsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Unit Tags"));

        // Current tags list
        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.setBorder(new TitledBorder("Current Tags"));

        currentTagsModel = new DefaultListModel<>();
        currentTagsList = new JList<>(currentTagsModel);
        currentTagsList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane scrollPane = new JScrollPane(currentTagsList);
        scrollPane.setPreferredSize(new Dimension(300, 200));
        listPanel.add(scrollPane, BorderLayout.CENTER);

        // Tag management buttons
        JPanel tagButtonPanel = new JPanel(new FlowLayout());
        removeTagButton = new JButton("Remove Selected");
        removeTagButton.addActionListener(this::removeSelectedTags);
        tagButtonPanel.add(removeTagButton);

        listPanel.add(tagButtonPanel, BorderLayout.SOUTH);
        panel.add(listPanel, BorderLayout.CENTER);
        JPanel addPanel = new JPanel(new BorderLayout());
        addPanel.setBorder(new TitledBorder("Add New Tag"));

        newTagField = new JTextField();
        newTagField.setToolTipText("Enter a new tag to add (e.g., 'SmokeGrenade', 'CustomRecon')");
        addPanel.add(newTagField, BorderLayout.CENTER);

        addTagButton = new JButton("Add Tag");
        addTagButton.addActionListener(this::addNewTag);
        addPanel.add(addTagButton, BorderLayout.EAST);

        panel.add(addPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createOrdersPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Unit Order Configuration"));

        // Top section with order fields
        JPanel fieldsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Explanation header
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3; gbc.weightx = 1.0;
        JLabel explanationLabel = new JLabel("<html><b>Change what orders these units can use:</b> " +
            "Each field shows the current order set reference. Click 'Change' to assign a different order set.</html>");
        explanationLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        fieldsPanel.add(explanationLabel, gbc);

        // Valid Orders
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1; gbc.weightx = 0.0;
        fieldsPanel.add(new JLabel("Basic Orders:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        validOrdersField = new JTextField();
        validOrdersField.setEditable(false);
        validOrdersField.setToolTipText("Current order set reference - defines what orders this unit can use");
        fieldsPanel.add(validOrdersField, gbc);

        gbc.gridx = 2; gbc.weightx = 0.0;
        editValidOrdersButton = new JButton("Change");
        editValidOrdersButton.setToolTipText("Change the order set for these units");
        editValidOrdersButton.addActionListener(e -> editOrderReference("Basic Orders", validOrdersField,
            "Enter the path to an OrderAvailability descriptor (e.g., ~/Descriptor_OrderAvailability_Infantry_SmokeGrenade)"));
        fieldsPanel.add(editValidOrdersButton, gbc);

        // Unlockable Orders
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0;
        fieldsPanel.add(new JLabel("Advanced Orders:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        unlockableOrdersField = new JTextField();
        unlockableOrdersField.setEditable(false);
        unlockableOrdersField.setToolTipText("Current advanced order set reference - defines special orders this unit can unlock");
        fieldsPanel.add(unlockableOrdersField, gbc);

        gbc.gridx = 2; gbc.weightx = 0.0;
        editUnlockableOrdersButton = new JButton("Change");
        editUnlockableOrdersButton.setToolTipText("Change the advanced order set for these units");
        editUnlockableOrdersButton.addActionListener(e -> editOrderReference("Advanced Orders", unlockableOrdersField,
            "Enter the path to an OrderAvailability descriptor for advanced/unlockable orders"));
        fieldsPanel.add(editUnlockableOrdersButton, gbc);

        panel.add(fieldsPanel, BorderLayout.NORTH);

        // Help text in scrollable area
        JTextArea helpText = new JTextArea(
            "What this does:\n" +
            "- Basic Orders: The standard orders a unit can use (move, attack, etc.)\n" +
            "- Advanced Orders: Special orders that can be unlocked (smoke grenades, special abilities)\n\n" +
            "How to use:\n" +
            "1. Click 'Change' next to the order type you want to modify\n" +
            "2. Enter the path to an existing OrderAvailability descriptor\n" +
            "3. Click 'Apply Changes' to update all selected units\n\n" +
            "Example order sets:\n" +
            "- ~/Descriptor_OrderAvailability_Infantry_SmokeGrenade (adds smoke grenades)\n" +
            "- ~/Descriptor_OrderAvailability_Recon_Enhanced (enhanced recon abilities)\n" +
            "- ~/Descriptor_OrderAvailability_Tank_Standard (standard tank orders)\n\n" +
            "Important Notes:\n" +
            "- Order sets must already exist in the game files\n" +
            "- Changes affect ALL units in your current selection\n" +
            "- Use tag filtering to limit which units are modified\n" +
            "- Test changes on a small group first before applying to many units"
        );
        helpText.setEditable(false);
        helpText.setFont(helpText.getFont().deriveFont(Font.PLAIN, 11f));
        helpText.setLineWrap(true);
        helpText.setWrapStyleWord(true);
        helpText.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JScrollPane helpScrollPane = new JScrollPane(helpText);
        helpScrollPane.setBorder(new TitledBorder("Help & Examples"));
        helpScrollPane.setPreferredSize(new Dimension(0, 200));
        panel.add(helpScrollPane, BorderLayout.CENTER);

        return panel;
    }

    private void loadCurrentData() {
        if (unitDescriptors.isEmpty()) {
            statusLabel.setText("No units selected");
            return;
        }
        ObjectValue firstUnit = unitDescriptors.get(0);
        Set<String> allUnitTags = TagExtractor.extractTagsFromUnit(firstUnit);
        // Filter out UNITE tags to match TagFilterDialog behavior
        Set<String> meaningfulTags = allUnitTags.stream()
            .filter(tag -> !tag.contains("UNITE"))
            .collect(java.util.stream.Collectors.toSet());

        currentTagsModel.clear();
        for (String tag : meaningfulTags) {
            currentTagsModel.addElement(tag);
        }
        if (currentUnit != null) {
            loadOrderReferences(currentUnit);
        }

        statusLabel.setText(String.format("Ready to modify tags for %d units and orders for %s",
            unitDescriptors.size(),
            currentUnit != null ? (currentUnit.getInstanceName() != null ? currentUnit.getInstanceName() : "Unknown") : "no unit"));
    }

    private void loadOrderReferences(ObjectValue unit) {
        String validOrders = findOrderReference(unit, "TOrderConfigModuleDescriptor", "ValidOrders");
        validOrdersField.setText(validOrders != null ? validOrders : "Not found");
        String unlockableOrders = findOrderReference(unit, "TOrderableModuleDescriptor", "UnlockableOrders");
        unlockableOrdersField.setText(unlockableOrders != null ? unlockableOrders : "Not found");
    }

    private String findOrderReference(ObjectValue unit, String moduleType, String propertyName) {
        NDFValue modulesValue = unit.getProperty("ModulesDescriptors");
        if (!(modulesValue instanceof ArrayValue)) {
            return null;
        }

        ArrayValue modules = (ArrayValue) modulesValue;
        for (NDFValue moduleValue : modules.getElements()) {
            if (moduleValue instanceof ObjectValue) {
                ObjectValue module = (ObjectValue) moduleValue;
                if (moduleType.equals(module.getTypeName())) {
                    NDFValue orderRef = module.getProperty(propertyName);
                    if (orderRef instanceof TemplateRefValue) {
                        return ((TemplateRefValue) orderRef).getPath();
                    } else if (orderRef instanceof StringValue) {
                        return ((StringValue) orderRef).getValue();
                    }
                }
            }
        }
        return null;
    }

    private void addNewTag(ActionEvent e) {
        String newTag = newTagField.getText().trim();
        if (newTag.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a tag name", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (currentTagsModel.contains(newTag)) {
            JOptionPane.showMessageDialog(this, "Tag already exists", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        currentTagsModel.addElement(newTag);
        newTagField.setText("");
        modified = true;
    }

    private void removeSelectedTags(ActionEvent e) {
        int[] selectedIndices = currentTagsList.getSelectedIndices();
        if (selectedIndices.length == 0) {
            JOptionPane.showMessageDialog(this, "Please select tags to remove", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        for (int i = selectedIndices.length - 1; i >= 0; i--) {
            currentTagsModel.removeElementAt(selectedIndices[i]);
        }
        modified = true;
    }

    private void editOrderReference(String orderType, JTextField field, String promptMessage) {
        String currentValue = field.getText();
        if ("Not found".equals(currentValue)) {
            currentValue = "";
        }

        String newValue = JOptionPane.showInputDialog(
            this,
            promptMessage + "\n\nCurrent value: " + (currentValue.isEmpty() ? "(none)" : currentValue),
            "Change " + orderType,
            JOptionPane.QUESTION_MESSAGE
        );

        if (newValue != null) {
            field.setText(newValue.trim());
            modified = true;
        }
    }

    private void applyChanges(ActionEvent e) {
        if (!modified) {
            dispose();
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
            this,
            String.format("Apply changes to %d units?", unitDescriptors.size()),
            "Confirm Changes",
            JOptionPane.YES_NO_OPTION
        );

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            applyTagChanges();
            applyOrderChanges();

            JOptionPane.showMessageDialog(
                this,
                "Changes applied successfully!",
                "Success",
                JOptionPane.INFORMATION_MESSAGE
            );

            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                this,
                "Error applying changes: " + ex.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void applyTagChanges() {
        Set<String> newTags = new HashSet<>();
        for (int i = 0; i < currentTagsModel.getSize(); i++) {
            newTags.add(currentTagsModel.getElementAt(i));
        }

        // Apply to all units
        for (ObjectValue unit : unitDescriptors) {
            updateUnitTags(unit, newTags);
        }
    }

    private void updateUnitTags(ObjectValue unit, Set<String> newTags) {
        NDFValue modulesValue = unit.getProperty("ModulesDescriptors");
        if (!(modulesValue instanceof ArrayValue)) {
            return;
        }

        ArrayValue modules = (ArrayValue) modulesValue;
        for (NDFValue moduleValue : modules.getElements()) {
            if (moduleValue instanceof ObjectValue) {
                ObjectValue module = (ObjectValue) moduleValue;
                if ("TTagsModuleDescriptor".equals(module.getTypeName())) {
                    ArrayValue newTagSet = NDFValue.createArray();
                    boolean first = true;
                    for (String tag : newTags) {
                        newTagSet.add(NDFValue.createString(tag), !first);
                        first = false;
                    }

                    // Track the change
                    NDFValue oldTagSet = module.getProperty("TagSet");
                    String unitName = unit.getInstanceName() != null ? unit.getInstanceName() : "Unknown Unit";
                    String path = "ModulesDescriptors[TTagsModuleDescriptor].TagSet";
                    modificationTracker.recordModification(unitName, path, oldTagSet, newTagSet);

                    // Apply the change
                    module.setProperty("TagSet", newTagSet);
                    break;
                }
            }
        }
    }

    private void applyOrderChanges() {
        if (currentUnit == null) {
            return; // No unit selected for order editing
        }

        String validOrders = validOrdersField.getText().trim();
        String unlockableOrders = unlockableOrdersField.getText().trim();

        if ("Not found".equals(validOrders)) validOrders = "";
        if ("Not found".equals(unlockableOrders)) unlockableOrders = "";

        // Apply order changes only to the current unit
        if (!validOrders.isEmpty()) {
            updateOrderReference(currentUnit, "TOrderConfigModuleDescriptor", "ValidOrders", validOrders);
        }
        if (!unlockableOrders.isEmpty()) {
            updateOrderReference(currentUnit, "TOrderableModuleDescriptor", "UnlockableOrders", unlockableOrders);
        }
    }

    private void updateOrderReference(ObjectValue unit, String moduleType, String propertyName, String newValue) {
        NDFValue modulesValue = unit.getProperty("ModulesDescriptors");
        if (!(modulesValue instanceof ArrayValue)) {
            return;
        }

        ArrayValue modules = (ArrayValue) modulesValue;
        for (NDFValue moduleValue : modules.getElements()) {
            if (moduleValue instanceof ObjectValue) {
                ObjectValue module = (ObjectValue) moduleValue;
                if (moduleType.equals(module.getTypeName())) {
                    NDFValue newRef = NDFValue.createTemplateRef(newValue);

                    // Track the change
                    NDFValue oldRef = module.getProperty(propertyName);
                    String unitName = unit.getInstanceName() != null ? unit.getInstanceName() : "Unknown Unit";
                    String path = "ModulesDescriptors[" + moduleType + "]." + propertyName;
                    modificationTracker.recordModification(unitName, path, oldRef, newRef);

                    // Apply the change
                    module.setProperty(propertyName, newRef);
                    break;
                }
            }
        }
    }

    private void openTagFilterDialog(ActionEvent e) {
        TagFilterDialog filterDialog = new TagFilterDialog((JFrame) getParent(), allUnitDescriptors);
        filterDialog.setVisible(true);

        if (filterDialog.isConfirmed()) {
            List<ObjectValue> filteredUnits = filterDialog.getFilteredUnits();
            this.unitDescriptors = new ArrayList<>(filteredUnits);
            updateUnitCount();
            loadCurrentData(); // Reload data for the new unit selection
        }
    }

    private void openUnitSelectorDialog(ActionEvent e) {
        String[] unitNames = allUnitDescriptors.stream()
            .map(unit -> unit.getInstanceName() != null ? unit.getInstanceName() : "Unknown Unit")
            .toArray(String[]::new);

        if (unitNames.length == 0) {
            JOptionPane.showMessageDialog(this, "No units available", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String selectedName = (String) JOptionPane.showInputDialog(
            this,
            "Select a unit to edit orders for:",
            "Select Unit",
            JOptionPane.QUESTION_MESSAGE,
            null,
            unitNames,
            currentUnit != null ? (currentUnit.getInstanceName() != null ? currentUnit.getInstanceName() : "Unknown Unit") : unitNames[0]
        );

        if (selectedName != null) {
            for (ObjectValue unit : allUnitDescriptors) {
                String unitName = unit.getInstanceName() != null ? unit.getInstanceName() : "Unknown Unit";
                if (unitName.equals(selectedName)) {
                    currentUnit = unit;
                    break;
                }
            }

            // Reload order data for the new unit
            if (currentUnit != null) {
                loadOrderReferences(currentUnit);
                statusLabel.setText(String.format("Ready to modify tags for %d units and orders for %s",
                    unitDescriptors.size(),
                    currentUnit.getInstanceName() != null ? currentUnit.getInstanceName() : "Unknown"));
            }
        }
    }

    private void updateUnitCount() {
        unitsLabel.setText(String.format("Editing tags for: %d units", unitDescriptors.size()));
        statusLabel.setText(String.format("Ready to modify tags for %d units and orders for %s",
            unitDescriptors.size(),
            currentUnit != null ? (currentUnit.getInstanceName() != null ? currentUnit.getInstanceName() : "Unknown") : "no unit"));
    }

    public boolean isModified() {
        return modified;
    }
}
