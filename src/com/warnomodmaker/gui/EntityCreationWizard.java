package com.warnomodmaker.gui;

import com.warnomodmaker.model.*;
import com.warnomodmaker.model.EntityCreationManager.EntityBlueprint;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-step wizard for entity creation with auto-loading of required files
 */
public class EntityCreationWizard extends JDialog {

    private final Frame parent;
    private final CrossSystemIntegrityManager integrityManager;
    private final EntityCreationManager entityManager;
    private final FileLoader fileLoader;
    
    // Wizard state
    private int currentStep = 1;
    private final int totalSteps = 3;
    
    // Step 1: Entity type and name selection
    private JComboBox<String> entityTypeCombo;
    private JTextField entityNameField;
    
    // Step 2: Configuration
    private EntityConfigurationPanel configurationPanel;
    
    // Step 3: Results
    private JTextArea resultsArea;
    
    // Navigation
    private JButton backButton;
    private JButton nextButton;
    private JButton finishButton;
    private JButton cancelButton;
    
    // Wizard data
    private String selectedEntityType;
    private String selectedEntityName;
    private Map<String, Object> entityConfiguration;
    private boolean entityCreated = false;
    
    public EntityCreationWizard(Frame parent, CrossSystemIntegrityManager integrityManager, FileLoader fileLoader) {
        super(parent, "Create Entity - Step 1 of 3", true);
        this.parent = parent;
        this.integrityManager = integrityManager;
        this.entityManager = new EntityCreationManager();
        this.entityConfiguration = new HashMap<>();
        this.fileLoader = fileLoader;
        
        initializeWizard();
        setupEventHandlers();
        
        pack();
        setLocationRelativeTo(parent);
    }
    
    private void initializeWizard() {
        setLayout(new BorderLayout());
        
        // Header
        JPanel headerPanel = createHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);
        
        // Content area (will change based on step)
        JPanel contentPanel = createStep1Panel();
        add(contentPanel, BorderLayout.CENTER);
        
        // Navigation
        JPanel navigationPanel = createNavigationPanel();
        add(navigationPanel, BorderLayout.SOUTH);
        
        updateNavigationButtons();
    }
    
    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.setBackground(new Color(245, 245, 245));

        JLabel titleLabel = new JLabel("Create New Entity");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        titleLabel.setForeground(new Color(50, 50, 50)); // Dark text for better readability
        panel.add(titleLabel, BorderLayout.WEST);

        JLabel stepLabel = new JLabel("Step " + currentStep + " of " + totalSteps);
        stepLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        stepLabel.setForeground(new Color(80, 80, 80)); // Darker grey for better readability
        panel.add(stepLabel, BorderLayout.EAST);

        return panel;
    }
    
    private JPanel createStep1Panel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Title
        JLabel titleLabel = new JLabel("Select Entity Type and Name");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(titleLabel, gbc);
        
        // Entity type selection
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        panel.add(new JLabel("Entity Type:"), gbc);
        
        entityTypeCombo = new JComboBox<>();
        // Add all entity types found in WARNO
        entityTypeCombo.addItem("Infantry");
        entityTypeCombo.addItem("Tank");
        entityTypeCombo.addItem("Artillery");
        entityTypeCombo.addItem("Air Defense");
        entityTypeCombo.addItem("Transport");
        entityTypeCombo.addItem("IFV");
        entityTypeCombo.addItem("Command Vehicle");
        entityTypeCombo.addItem("Reconnaissance");
        entityTypeCombo.addItem("Supply");
        entityTypeCombo.addItem("Logistics");
        entityTypeCombo.addItem("Command");
        entityTypeCombo.addItem("Aircraft");
        entityTypeCombo.addItem("SEAD Aircraft");
        entityTypeCombo.addItem("Fighter Aircraft");
        entityTypeCombo.addItem("Bomber Aircraft");
        entityTypeCombo.addItem("UAV");
        entityTypeCombo.addItem("Helicopter");
        entityTypeCombo.addItem("Command Helicopter");
        entityTypeCombo.addItem("Anti-Tank");
        
        gbc.gridx = 1; gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(entityTypeCombo, gbc);
        
        // Entity name
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Entity Name:"), gbc);
        
        entityNameField = new JTextField(20);
        gbc.gridx = 1; gbc.gridy = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(entityNameField, gbc);
        
        // Description
        JTextArea descArea = new JTextArea(
            "Choose the type of entity you want to create and give it a unique name.\n\n" +
            "The wizard will automatically load required files and guide you through\n" +
            "configuring the entity's properties."
        );
        descArea.setEditable(false);
        descArea.setOpaque(false);
        descArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        descArea.setForeground(Color.GRAY);
        
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        panel.add(descArea, gbc);
        
        return panel;
    }
    
    private JPanel createNavigationPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Left side - Back button
        backButton = new JButton("< Back");
        backButton.setPreferredSize(new Dimension(80, 30));
        panel.add(backButton, BorderLayout.WEST);
        
        // Right side - Next/Finish and Cancel buttons
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        nextButton = new JButton("Next >");
        nextButton.setPreferredSize(new Dimension(80, 30));
        rightPanel.add(nextButton);
        
        finishButton = new JButton("Finish");
        finishButton.setPreferredSize(new Dimension(80, 30));
        finishButton.setVisible(false);
        rightPanel.add(finishButton);
        
        cancelButton = new JButton("Cancel");
        cancelButton.setPreferredSize(new Dimension(80, 30));
        rightPanel.add(cancelButton);
        
        panel.add(rightPanel, BorderLayout.EAST);
        
        return panel;
    }
    
    private void setupEventHandlers() {
        backButton.addActionListener(this::goBack);
        nextButton.addActionListener(this::goNext);
        finishButton.addActionListener(this::finish);
        cancelButton.addActionListener(e -> dispose());
    }
    
    private void goBack(ActionEvent e) {
        if (currentStep > 1) {
            currentStep--;
            updateWizardContent();
            updateNavigationButtons();
        }
    }
    
    private void goNext(ActionEvent e) {
        if (validateCurrentStep()) {
            if (currentStep == 1) {
                // Store step 1 data
                selectedEntityType = (String) entityTypeCombo.getSelectedItem();
                selectedEntityName = entityNameField.getText().trim();

                // Auto-load required files for step 2
                autoLoadRequiredFiles();
            } else if (currentStep == 2) {
                // Store step 2 data
                entityConfiguration = configurationPanel.getConfiguration();
            }

            currentStep++;
            updateWizardContent();
            updateNavigationButtons();

            // Create entity after step 3 UI is created
            if (currentStep == 3) {
                // Initialize entity manager with open files before creating entity
                initializeEntityManager();
                createEntity();
            }
        }
    }
    
    private void finish(ActionEvent e) {
        entityCreated = true;
        dispose();
    }
    
    private boolean validateCurrentStep() {
        switch (currentStep) {
            case 1:
                if (entityNameField.getText().trim().isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Please enter an entity name.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
                return true;
            case 2:
                return configurationPanel.validateConfiguration();
            default:
                return true;
        }
    }
    
    private void updateWizardContent() {
        // Remove only the CENTER component (content panel)
        BorderLayout layout = (BorderLayout) getContentPane().getLayout();
        Component centerComponent = layout.getLayoutComponent(BorderLayout.CENTER);
        if (centerComponent != null) {
            getContentPane().remove(centerComponent);
        }
        
        // Add new content based on step
        JPanel contentPanel;
        switch (currentStep) {
            case 1:
                contentPanel = createStep1Panel();
                setTitle("Create Entity - Step 1 of 3");
                break;
            case 2:
                contentPanel = createStep2Panel();
                setTitle("Create Entity - Step 2 of 3");
                break;
            case 3:
                contentPanel = createStep3Panel();
                setTitle("Create Entity - Step 3 of 3");
                break;
            default:
                contentPanel = new JPanel();
        }
        
        add(contentPanel, BorderLayout.CENTER);
        
        // Update header
        updateHeaderPanel();
        
        revalidate();
        repaint();
        pack();
    }
    
    private void updateHeaderPanel() {
        JPanel headerPanel = (JPanel) getContentPane().getComponent(0);
        JLabel stepLabel = (JLabel) ((BorderLayout) headerPanel.getLayout()).getLayoutComponent(BorderLayout.EAST);
        stepLabel.setText("Step " + currentStep + " of " + totalSteps);
    }
    
    private void updateNavigationButtons() {
        backButton.setEnabled(currentStep > 1);
        nextButton.setVisible(currentStep < totalSteps);
        finishButton.setVisible(currentStep == totalSteps);
    }
    
    public boolean wasEntityCreated() {
        return entityCreated;
    }
    
    public String getCreatedEntityName() {
        return selectedEntityName;
    }
    
    public String getCreatedEntityType() {
        return selectedEntityType;
    }

    private JPanel createStep2Panel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Title
        JLabel titleLabel = new JLabel("Configure Entity Properties");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        panel.add(titleLabel, BorderLayout.NORTH);

        // Configuration panel
        configurationPanel = new EntityConfigurationPanel(selectedEntityType, selectedEntityName);
        panel.add(configurationPanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createStep3Panel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Title
        JLabel titleLabel = new JLabel("Entity Creation Results");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        panel.add(titleLabel, BorderLayout.NORTH);

        // Results area
        resultsArea = new JTextArea(15, 50);
        resultsArea.setEditable(false);
        resultsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        resultsArea.setBackground(new Color(248, 248, 248));
        resultsArea.setForeground(new Color(50, 50, 50)); // Dark text for better readability

        JScrollPane scrollPane = new JScrollPane(resultsArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Creation Summary"));
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private void autoLoadRequiredFiles() {
        // Get required files for this entity type
        String[] requiredFiles = getRequiredFilesForEntityType(selectedEntityType);

        StringBuilder message = new StringBuilder();
        message.append("The following files are required for ").append(selectedEntityType).append(" entities:\n\n");

        for (String fileName : requiredFiles) {
            message.append("• ").append(fileName).append("\n");
        }

        message.append("\nWould you like to automatically load these files?");

        int result = JOptionPane.showConfirmDialog(this,
            message.toString(),
            "Auto-Load Required Files",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            // Request file loader to load the required files
            if (fileLoader != null) {
                fileLoader.autoLoadRequiredFiles(requiredFiles);
            }
        }
    }

    private String[] getRequiredFilesForEntityType(String entityType) {
        switch (entityType) {
            case "Infantry":
                return new String[]{
                    "UniteDescriptor.ndf",
                    "WeaponDescriptor.ndf",
                    "Ammunition.ndf",
                    "DepictionInfantry.ndf",
                    "CapaciteList.ndf",
                    "EffetsSurUnite.ndf",
                    "GameData/Generated/Sound/SoundDescriptors.ndf"
                };

            case "Tank":
                return new String[]{
                    "UniteDescriptor.ndf",
                    "WeaponDescriptor.ndf",
                    "Ammunition.ndf",
                    "NdfDepictionList.ndf",
                    "CapaciteList.ndf",
                    "EffetsSurUnite.ndf",
                    "GameData/Generated/Sound/SoundDescriptors.ndf"
                };

            case "Artillery":
            case "Air Defense":
            case "Transport":
            case "IFV":
            case "Command Vehicle":
            case "Reconnaissance":
            case "Anti-Tank":
                return new String[]{
                    "UniteDescriptor.ndf",
                    "WeaponDescriptor.ndf",
                    "Ammunition.ndf",
                    "AmmunitionMissiles.ndf",
                    "NdfDepictionList.ndf",
                    "CapaciteList.ndf",
                    "EffetsSurUnite.ndf",
                    "GameData/Generated/Sound/SoundDescriptors.ndf"
                };

            case "Supply":
            case "Logistics":
            case "Command":
                return new String[]{
                    "UniteDescriptor.ndf",
                    "NdfDepictionList.ndf",
                    "CapaciteList.ndf",
                    "EffetsSurUnite.ndf",
                    "GameData/Generated/Sound/SoundDescriptors.ndf"
                };

            case "Aircraft":
            case "Fighter Aircraft":
            case "Bomber Aircraft":
            case "SEAD Aircraft":
            case "UAV":
            case "Helicopter":
            case "Command Helicopter":
                return new String[]{
                    "UniteDescriptor.ndf",
                    "WeaponDescriptor.ndf",
                    "Ammunition.ndf",
                    "AmmunitionMissiles.ndf",
                    "NdfDepictionList.ndf",
                    "CapaciteList.ndf",
                    "EffetsSurUnite.ndf",
                    "GameData/Generated/Sound/SoundDescriptors.ndf"
                };

            default:
                return new String[]{
                    "UniteDescriptor.ndf",
                    "WeaponDescriptor.ndf",
                    "Ammunition.ndf",
                    "GameData/Generated/Sound/SoundDescriptors.ndf"
                };
        }
    }

    private void createEntity() {
        StringBuilder results = new StringBuilder();
        results.append("Entity Creation Summary\n");
        results.append("======================\n\n");
        results.append("Entity Type: ").append(selectedEntityType).append("\n");
        results.append("Entity Name: ").append(selectedEntityName).append("\n\n");

        results.append("Configuration:\n");
        for (Map.Entry<String, Object> entry : entityConfiguration.entrySet()) {
            results.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }

        try {
            // Prepare custom properties from configuration
            Map<String, Object> customProperties = new HashMap<>();
            customProperties.put("Health", entityConfiguration.get("Health"));
            customProperties.put("Armor", entityConfiguration.get("Armor"));
            customProperties.put("Speed", entityConfiguration.get("Speed"));
            customProperties.put("Cost", entityConfiguration.get("Cost"));
            customProperties.put("ECM", entityConfiguration.get("ECM"));
            customProperties.put("Faction", entityConfiguration.get("Faction"));
            customProperties.put("Nation", entityConfiguration.get("Nation"));



            // Use the cached open files and trackers (same references as initialization)
            Map<String, List<NDFValue.ObjectValue>> openFiles = cachedOpenFiles;
            Map<String, ModificationTracker> trackers = cachedTrackers;



            // Attempt to create the entity using the EntityCreationManager
            EntityCreationManager.EntityCreationResult creationResult =
                entityManager.createCompleteEntity(selectedEntityType, selectedEntityName,
                                                 customProperties, openFiles, trackers);


            results.append("\nEntity Creation Results:\n");
            if (creationResult.isSuccess()) {
                results.append("✓ Entity created successfully!\n\n");

                results.append("Files that were modified:\n");
                for (Map.Entry<String, String> entry : creationResult.getCreatedObjects().entrySet()) {
                    results.append("  ✓ ").append(entry.getKey()).append(" → ").append(entry.getValue()).append("\n");
                }

                if (creationResult.hasPendingCreations()) {
                    results.append("\nFiles that need to be created/loaded:\n");
                    for (EntityCreationManager.PendingFileCreation pending : creationResult.getPendingCreations()) {
                        results.append("  ⚠ ").append(pending.getDisplayName()).append("\n");
                    }
                }

                entityCreated = true;
            } else {
                results.append("✗ Entity creation failed\n");
                results.append("Errors:\n");
                for (String error : creationResult.getErrors()) {
                    results.append("  - ").append(error).append("\n");
                }
                if (creationResult.getErrors().isEmpty()) {
                    results.append("  - No specific error message provided\n");
                }
                entityCreated = false;
            }

        } catch (Exception e) {
            results.append("\n✗ Entity creation failed with exception:\n");
            results.append("  ").append(e.getMessage()).append("\n");
            entityCreated = false;
        }

        resultsArea.setText(results.toString());
    }

    // Store the open files reference to reuse for entity creation
    private Map<String, List<NDFValue.ObjectValue>> cachedOpenFiles;
    private Map<String, ModificationTracker> cachedTrackers;

    /**
     * Initialize the entity manager with currently open files
     */
    private void initializeEntityManager() {
        // Get open files from the file loader and cache them
        cachedOpenFiles = fileLoader.getOpenFiles();
        cachedTrackers = fileLoader.getModificationTrackers();

        if (cachedOpenFiles.isEmpty()) {
            return;
        }

        // Analyze open files to create blueprints
        entityManager.analyzeOpenFiles(cachedOpenFiles);
    }

    /**
     * Get modification trackers for open files
     */
    private Map<String, ModificationTracker> getModificationTrackers() {
        return fileLoader.getModificationTrackers();
    }
}
