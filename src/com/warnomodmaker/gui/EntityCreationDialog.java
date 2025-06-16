package com.warnomodmaker.gui;

import com.warnomodmaker.model.*;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * Dialog for creating complete entities across multiple NDF files.
 * This is the Phase 3/4 comprehensive entity creation system.
 */
public class EntityCreationDialog extends JDialog {
    
    private final Map<String, List<NDFValue.ObjectValue>> openFiles;
    private final Map<String, ModificationTracker> trackers;
    private final EntityCreationManager entityManager;
    private final CrossSystemIntegrityManager integrityManager;
    
    // UI Components
    private JComboBox<String> entityTypeCombo;
    private JTextField entityNameField;
    private JTextArea descriptionArea;
    private JPanel fileRequirementsPanel;
    private JTextArea propertiesArea;
    private JButton createButton;
    private JButton cancelButton;
    private JLabel statusLabel;
    
    private boolean entityCreated = false;
    
    public EntityCreationDialog(Frame parent, Map<String, List<NDFValue.ObjectValue>> openFiles,
                              Map<String, ModificationTracker> trackers, CrossSystemIntegrityManager integrityManager) {
        super(parent, "Create Complete Entity", true);
        this.openFiles = openFiles;
        this.trackers = trackers;
        this.integrityManager = integrityManager;
        this.entityManager = new EntityCreationManager();

        // Analyze open files to discover entity creation patterns
        entityManager.analyzeOpenFiles(openFiles);

        initializeUI();
        setupEventHandlers();

        // Update entity type selection after all UI components are created
        SwingUtilities.invokeLater(this::updateEntityTypeSelection);

        pack();
        setLocationRelativeTo(parent);
    }
    
    private void initializeUI() {
        setLayout(new BorderLayout());
        
        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        
        // Top panel - Entity selection
        JPanel topPanel = createEntitySelectionPanel();
        mainPanel.add(topPanel, BorderLayout.NORTH);
        
        // Center panel - Configuration
        JPanel centerPanel = createConfigurationPanel();
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        
        // Bottom panel - Actions
        JPanel bottomPanel = createActionPanel();
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        add(mainPanel, BorderLayout.CENTER);
    }
    
    private JPanel createEntitySelectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Entity Type"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Entity type selection
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Entity Type:"), gbc);
        
        entityTypeCombo = new JComboBox<>();
        Set<String> availableTypes = entityManager.getAvailableEntityTypes();

        if (availableTypes.isEmpty()) {
            entityTypeCombo.addItem("No entity patterns discovered - need more files open");
        } else {
            // Sort entity types for better UX
            availableTypes.stream().sorted().forEach(entityTypeCombo::addItem);
        }
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(entityTypeCombo, gbc);
        
        // Entity name
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0;
        panel.add(new JLabel("Entity Name:"), gbc);
        
        entityNameField = new JTextField(20);
        entityNameField.setText("NewEntity");
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(entityNameField, gbc);
        
        // Description
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0;
        panel.add(new JLabel("Description:"), gbc);
        
        descriptionArea = new JTextArea(3, 30);
        descriptionArea.setEditable(false);
        descriptionArea.setBackground(getBackground());
        descriptionArea.setForeground(Color.BLACK);
        descriptionArea.setFont(descriptionArea.getFont().deriveFont(Font.PLAIN));
        JScrollPane descScrollPane = new JScrollPane(descriptionArea);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 0.3;
        panel.add(descScrollPane, gbc);
        
        return panel;
    }
    
    private JPanel createConfigurationPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Dependencies panel with clean styling
        JPanel depsPanel = new JPanel(new BorderLayout());
        depsPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Dependencies",
            TitledBorder.LEFT, TitledBorder.TOP,
            new Font(Font.SANS_SERIF, Font.BOLD, 12), new Color(60, 60, 60)));

        fileRequirementsPanel = new JPanel();
        fileRequirementsPanel.setLayout(new BoxLayout(fileRequirementsPanel, BoxLayout.Y_AXIS));
        fileRequirementsPanel.setBackground(new Color(60, 63, 65)); // Match the dark theme

        JScrollPane reqScrollPane = new JScrollPane(fileRequirementsPanel);
        reqScrollPane.setPreferredSize(new Dimension(450, 180));
        reqScrollPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
            BorderFactory.createEmptyBorder(5, 5, 5, 5))); // Clean border
        reqScrollPane.getViewport().setBackground(new Color(60, 63, 65));

        // CRITICAL FIX: Reset scroll position to top every time
        SwingUtilities.invokeLater(() -> {
            reqScrollPane.getVerticalScrollBar().setValue(0);
            reqScrollPane.getHorizontalScrollBar().setValue(0);
        });
        depsPanel.add(reqScrollPane, BorderLayout.CENTER);
        panel.add(depsPanel, BorderLayout.NORTH);

        // Template Preview panel with much better readability
        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "What Will Be Created",
            TitledBorder.LEFT, TitledBorder.TOP,
            new Font(Font.SANS_SERIF, Font.BOLD, 12), new Color(60, 60, 60)));

        propertiesArea = new JTextArea(6, 40);
        propertiesArea.setEditable(false);
        propertiesArea.setBackground(new Color(60, 63, 65)); // Match the dark theme
        propertiesArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12)); // Readable font
        propertiesArea.setForeground(new Color(220, 220, 220)); // Light text for dark background
        propertiesArea.setText("Select an entity type to see what will be created...");
        propertiesArea.setLineWrap(true);
        propertiesArea.setWrapStyleWord(true);

        JScrollPane propsScrollPane = new JScrollPane(propertiesArea);
        propsScrollPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
            BorderFactory.createEmptyBorder(5, 5, 5, 5))); // Clean border
        propsScrollPane.getViewport().setBackground(new Color(60, 63, 65));

        // CRITICAL FIX: Reset scroll position to top every time
        SwingUtilities.invokeLater(() -> {
            propsScrollPane.getVerticalScrollBar().setValue(0);
            propsScrollPane.getHorizontalScrollBar().setValue(0);
        });
        previewPanel.add(propsScrollPane, BorderLayout.CENTER);
        panel.add(previewPanel, BorderLayout.CENTER);

        return panel;
    }
    
    private JPanel createActionPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Status label
        statusLabel = new JLabel(" ");
        statusLabel.setForeground(Color.BLUE);
        panel.add(statusLabel, BorderLayout.WEST);
        
        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        createButton = new JButton("Create Entity");
        createButton.setPreferredSize(new Dimension(120, 30));
        buttonPanel.add(createButton);
        
        cancelButton = new JButton("Cancel");
        cancelButton.setPreferredSize(new Dimension(80, 30));
        buttonPanel.add(cancelButton);
        
        panel.add(buttonPanel, BorderLayout.EAST);
        
        return panel;
    }
    
    private void setupEventHandlers() {
        entityTypeCombo.addActionListener(e -> updateEntityTypeSelection());

        // Update preview when entity name changes
        entityNameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updatePreviewIfNeeded(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updatePreviewIfNeeded(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updatePreviewIfNeeded(); }
        });

        createButton.addActionListener(this::createEntity);
        cancelButton.addActionListener(e -> dispose());
    }

    private void updatePreviewIfNeeded() {
        String selectedType = (String) entityTypeCombo.getSelectedItem();
        if (selectedType != null && !selectedType.contains("No entity patterns")) {
            EntityCreationManager.EntityBlueprint blueprint = entityManager.getEntityBlueprint(selectedType);
            if (blueprint != null) {
                updateTemplatePreview(blueprint);
            }
        }
    }
    
    private void updateEntityTypeSelection() {
        String selectedType = (String) entityTypeCombo.getSelectedItem();
        if (selectedType == null || selectedType.contains("No entity patterns")) {
            descriptionArea.setText("No entity patterns discovered from the open files.\n\n" +
                "To discover entity patterns:\n" +
                "1. Open UniteDescriptorOLD.ndf with actual unit data\n" +
                "2. Optionally open related files (Ammunition.ndf, WeaponDescriptor.ndf)\n" +
                "3. Reopen this dialog to see discovered patterns");

            fileRequirementsPanel.removeAll();
            JLabel noReqLabel = new JLabel("Open more NDF files to discover entity patterns");
            noReqLabel.setForeground(Color.GRAY);
            fileRequirementsPanel.add(noReqLabel);
            fileRequirementsPanel.revalidate();
            fileRequirementsPanel.repaint();

            if (statusLabel != null) {
                statusLabel.setText("No entity patterns available");
                statusLabel.setForeground(Color.GRAY);
            }
            if (createButton != null) {
                createButton.setEnabled(false);
            }
            return;
        }

        EntityCreationManager.EntityBlueprint blueprint = entityManager.getEntityBlueprint(selectedType);
        if (blueprint == null) return;

        // Update description
        descriptionArea.setText(blueprint.getDescription());

        // Update file requirements display
        updateFileRequirementsDisplay(blueprint);

        // Update status
        checkFileAvailability(blueprint);
    }
    
    private void updateFileRequirementsDisplay(EntityCreationManager.EntityBlueprint blueprint) {
        fileRequirementsPanel.removeAll();

        if (blueprint.getFileRequirements().isEmpty()) {
            JLabel noReqLabel = new JLabel("No file requirements discovered for this entity type");
            noReqLabel.setForeground(new Color(120, 120, 120));
            noReqLabel.setHorizontalAlignment(SwingConstants.CENTER);
            fileRequirementsPanel.add(noReqLabel);
        } else {
            // Show required files first, then optional - no awkward headers
            List<EntityCreationManager.FileRequirement> requiredFiles = blueprint.getRequiredFiles();
            List<EntityCreationManager.FileRequirement> optionalFiles = blueprint.getOptionalFiles();

            // Add required files
            for (EntityCreationManager.FileRequirement req : requiredFiles) {
                addFileRequirementRow(req, true);
            }

            // Add optional files with subtle separation
            if (!optionalFiles.isEmpty() && !requiredFiles.isEmpty()) {
                fileRequirementsPanel.add(Box.createVerticalStrut(8));
            }

            for (EntityCreationManager.FileRequirement req : optionalFiles) {
                addFileRequirementRow(req, false);
            }
        }

        fileRequirementsPanel.revalidate();
        fileRequirementsPanel.repaint();

        // Update template preview
        updateTemplatePreview(blueprint);
    }

    private void addFileRequirementRow(EntityCreationManager.FileRequirement req, boolean isRequired) {
        JPanel reqPanel = new JPanel(new BorderLayout(12, 0));
        reqPanel.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        reqPanel.setBackground(new Color(60, 63, 65)); // Match dark theme
        reqPanel.setPreferredSize(new Dimension(420, 42));

        // Left side - file name with color-coded required/optional text
        String fileName = req.getFileType() + ".ndf";
        String requiredText = isRequired ? " (Required)" : " (Optional)";

        // Create HTML for color coding
        String colorCode = isRequired ? "#dc3545" : "#6c757d"; // Red for required, gray for optional
        String htmlText = "<html>" + fileName + " <span style='color:" + colorCode + ";'>" + requiredText + "</span></html>";

        JLabel fileLabel = new JLabel(htmlText);
        fileLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        fileLabel.setForeground(new Color(220, 220, 220)); // Light text for dark background

        // Right side - clean status
        boolean fileOpen = openFiles.containsKey(req.getFileType());
        String statusText;
        Color statusColor;

        if (fileOpen) {
            statusText = "✓ Available";
            statusColor = new Color(80, 200, 120); // Brighter green for dark background
        } else if (isRequired) {
            statusText = "✗ Not Open";
            statusColor = new Color(255, 100, 100); // Brighter red for dark background
        } else {
            statusText = "○ Not Open";
            statusColor = new Color(160, 160, 160); // Lighter gray for dark background
        }

        JLabel statusIndicator = new JLabel(statusText);
        statusIndicator.setForeground(statusColor);
        statusIndicator.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        statusIndicator.setHorizontalAlignment(SwingConstants.RIGHT);

        reqPanel.add(fileLabel, BorderLayout.WEST);
        reqPanel.add(statusIndicator, BorderLayout.EAST);

        fileRequirementsPanel.add(reqPanel);

        // Add a simple separator using a border instead of a panel
        JPanel separatorContainer = new JPanel();
        separatorContainer.setBackground(new Color(60, 63, 65));
        separatorContainer.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(80, 83, 85)));
        separatorContainer.setMaximumSize(new Dimension(Short.MAX_VALUE, 1));
        fileRequirementsPanel.add(separatorContainer);

        fileRequirementsPanel.add(Box.createVerticalStrut(4)); // Slightly less spacing since we have separator
    }

    /**
     * Update template preview to show exactly what will be created
     */
    private void updateTemplatePreview(EntityCreationManager.EntityBlueprint blueprint) {
        String entityName = entityNameField.getText().trim();
        if (entityName.isEmpty()) {
            entityName = "YourEntity";
        }

        StringBuilder preview = new StringBuilder();
        preview.append("Creating ").append(blueprint.getDisplayName()).append(" entity: ").append(entityName).append("\n\n");

        List<EntityCreationManager.FileRequirement> requiredFiles = blueprint.getRequiredFiles();
        List<EntityCreationManager.FileRequirement> optionalFiles = blueprint.getOptionalFiles();

        // Show what will be created in each file
        if (!requiredFiles.isEmpty()) {
            preview.append("REQUIRED FILES - Will create objects:\n");
            for (EntityCreationManager.FileRequirement req : requiredFiles) {
                if (openFiles.containsKey(req.getFileType())) {
                    String templateName = generatePreviewTemplateName(entityName, req.getFileType());
                    String description = getFileDescription(req.getFileType());
                    preview.append("  ✓ ").append(req.getFileType()).append(".ndf\n");
                    preview.append("    → Object: ").append(templateName).append("\n");
                    preview.append("    → Purpose: ").append(description).append("\n\n");
                } else {
                    preview.append("  ✗ ").append(req.getFileType()).append(".ndf (NOT OPEN - Required!)\n\n");
                }
            }
        }

        // Show available optional files
        List<EntityCreationManager.FileRequirement> availableOptional = optionalFiles.stream()
            .filter(req -> openFiles.containsKey(req.getFileType()))
            .collect(java.util.stream.Collectors.toList());

        if (!availableOptional.isEmpty()) {
            preview.append("OPTIONAL FILES - Will also create:\n");
            for (EntityCreationManager.FileRequirement req : availableOptional) {
                String templateName = generatePreviewTemplateName(entityName, req.getFileType());
                String description = getFileDescription(req.getFileType());
                preview.append("  ✓ ").append(req.getFileType()).append(".ndf\n");
                preview.append("    → Object: ").append(templateName).append("\n");
                preview.append("    → Purpose: ").append(description).append("\n\n");
            }
        }

        // Show missing optional files
        List<EntityCreationManager.FileRequirement> missingOptional = optionalFiles.stream()
            .filter(req -> !openFiles.containsKey(req.getFileType()))
            .collect(java.util.stream.Collectors.toList());

        if (!missingOptional.isEmpty()) {
            preview.append("OPTIONAL FILES - Not open (will skip):\n");
            for (EntityCreationManager.FileRequirement req : missingOptional) {
                String description = getFileDescription(req.getFileType());
                preview.append("  ○ ").append(req.getFileType()).append(".ndf - ").append(description).append("\n");
            }
        }

        propertiesArea.setText(preview.toString());
    }

    /**
     * Get description of what each file type does
     */
    private String getFileDescription(String fileType) {
        switch (fileType) {
            case "UniteDescriptor": return "Main unit definition with stats and modules";
            case "WeaponDescriptor": return "Weapon systems and firing mechanics";
            case "Ammunition": return "Ammunition types and damage values";
            case "NdfDepictionList": return "Visual appearance and 3D models";
            case "EffetsSurUnite": return "Unit effects and status modifiers";
            case "SoundDescriptors": return "Audio effects and sound clips";
            case "WeaponSoundHappenings": return "Weapon sound mappings and audio events";
            case "GeneratedInfantryDepiction": return "Infantry-specific visual models";
            case "VehicleDepiction": return "Vehicle-specific visual models";
            case "InfantryAnimationDescriptor": return "Infantry movement animations";
            case "VehicleAnimationDescriptor": return "Vehicle movement animations";
            default: return "Specialized functionality for this unit type";
        }
    }

    /**
     * Generate template name for preview (simplified version)
     */
    private String generatePreviewTemplateName(String entityName, String fileType) {
        switch (fileType) {
            case "UniteDescriptor": return "Descriptor_Unit_" + entityName;
            case "WeaponDescriptor": return "WeaponDescriptor_" + entityName;
            case "Ammunition": return "Ammunition_" + entityName;
            case "GeneratedInfantryDepiction": return "InfantryDepiction_" + entityName;
            case "EffectDescriptor": return "Effect_" + entityName;
            case "SoundDescriptor": return "Sound_" + entityName;
            default: return fileType + "_" + entityName;
        }
    }
    
    private void checkFileAvailability(EntityCreationManager.EntityBlueprint blueprint) {
        List<EntityCreationManager.FileRequirement> requiredFiles = blueprint.getRequiredFiles();
        List<EntityCreationManager.FileRequirement> optionalFiles = blueprint.getOptionalFiles();

        int availableRequired = 0;
        int availableOptional = 0;

        for (EntityCreationManager.FileRequirement req : requiredFiles) {
            if (openFiles.containsKey(req.getFileType())) {
                availableRequired++;
            }
        }

        for (EntityCreationManager.FileRequirement req : optionalFiles) {
            if (openFiles.containsKey(req.getFileType())) {
                availableOptional++;
            }
        }

        if (availableRequired == requiredFiles.size()) {
            String statusText = "✓ All required files are open";
            if (availableOptional > 0) {
                statusText += " (+" + availableOptional + " optional)";
            }
            statusLabel.setText(statusText);
            statusLabel.setForeground(new Color(0, 120, 0));
            createButton.setEnabled(true);
        } else {
            int missingRequired = requiredFiles.size() - availableRequired;
            statusLabel.setText("⚠ " + missingRequired + " required files not open");
            statusLabel.setForeground(Color.RED);
            createButton.setEnabled(false);
        }
    }
    
    private void createEntity(ActionEvent e) {
        String entityType = (String) entityTypeCombo.getSelectedItem();
        String entityName = entityNameField.getText().trim();

        if (entityName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter an entity name.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Get blueprint for confirmation details
        EntityCreationManager.EntityBlueprint blueprint = entityManager.getEntityBlueprint(entityType);
        if (blueprint == null) {
            JOptionPane.showMessageDialog(this, "Entity blueprint not found.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Show enhanced confirmation dialog
        StringBuilder confirmMessage = new StringBuilder();
        confirmMessage.append("Create ").append(blueprint.getDisplayName()).append(" entity '").append(entityName).append("'?\n\n");

        long requiredCount = blueprint.getRequiredFiles().stream().filter(req -> openFiles.containsKey(req.getFileType())).count();
        long optionalCount = blueprint.getOptionalFiles().stream().filter(req -> openFiles.containsKey(req.getFileType())).count();

        confirmMessage.append("This will create objects in ").append(requiredCount + optionalCount).append(" files");
        if (optionalCount > 0) {
            confirmMessage.append(" (").append(requiredCount).append(" required + ").append(optionalCount).append(" optional)");
        }
        confirmMessage.append(".");

        int confirm = JOptionPane.showConfirmDialog(this,
            confirmMessage.toString(),
            "Confirm Entity Creation",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        // CRITICAL: Validate entity creation with integrity manager
        statusLabel.setText("Validating entity integrity...");
        statusLabel.setForeground(new Color(70, 130, 180));
        createButton.setEnabled(false);

        CrossSystemIntegrityManager.EntityCreationValidationResult validationResult =
            integrityManager.validateEntityCreation(entityType, entityName);

        if (!validationResult.isValid()) {
            StringBuilder errorMessage = new StringBuilder();
            errorMessage.append("Entity creation validation failed:\n\n");
            for (String issue : validationResult.getIssues()) {
                errorMessage.append("• ").append(issue).append("\n");
            }

            if (!validationResult.getWarnings().isEmpty()) {
                errorMessage.append("\nWarnings:\n");
                for (String warning : validationResult.getWarnings()) {
                    errorMessage.append("• ").append(warning).append("\n");
                }
            }

            JOptionPane.showMessageDialog(this, errorMessage.toString(),
                "Entity Creation Validation Failed", JOptionPane.ERROR_MESSAGE);

            statusLabel.setText("✗ Validation failed");
            statusLabel.setForeground(Color.RED);
            createButton.setEnabled(true);
            return;
        }

        // Create the entity with empty custom properties (system will use learned defaults)
        statusLabel.setText("Creating entity...");
        statusLabel.setForeground(new Color(70, 130, 180));

        EntityCreationManager.EntityCreationResult result = entityManager.createCompleteEntity(
            entityType, entityName, new HashMap<>(), openFiles, trackers);

        if (result.isSuccess()) {
            statusLabel.setText("✓ Entity created successfully!");
            statusLabel.setForeground(new Color(40, 167, 69));
            entityCreated = true;

            // Show success dialog with details
            showCreationResults(result);

            // Close dialog after short delay
            javax.swing.Timer timer = new javax.swing.Timer(2000, evt -> dispose());
            timer.setRepeats(false);
            timer.start();
        } else {
            statusLabel.setText("✗ Entity creation failed");
            statusLabel.setForeground(new Color(220, 53, 69));
            createButton.setEnabled(true);

            // Show error details
            showCreationErrors(result);
        }
    }
    

    
    private void showCreationResults(EntityCreationManager.EntityCreationResult result) {
        StringBuilder message = new StringBuilder();
        message.append("Entity '").append(result.getEntityName()).append("' created successfully!\n\n");
        message.append("Created objects:\n");

        for (Map.Entry<String, String> entry : result.getCreatedObjects().entrySet()) {
            message.append("• ").append(entry.getKey()).append(".ndf: ").append(entry.getValue()).append("\n");
        }

        JOptionPane.showMessageDialog(this, message.toString(), "Entity Created", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showCreationErrors(EntityCreationManager.EntityCreationResult result) {
        StringBuilder message = new StringBuilder();
        message.append("Failed to create entity '").append(result.getEntityName()).append("'\n\n");
        message.append("Errors:\n");

        for (String error : result.getErrors()) {
            message.append("• ").append(error).append("\n");
        }

        JOptionPane.showMessageDialog(this, message.toString(), "Creation Failed", JOptionPane.ERROR_MESSAGE);
    }
    
    public boolean wasEntityCreated() {
        return entityCreated;
    }
}
