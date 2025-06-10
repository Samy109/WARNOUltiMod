package com.warnomodmaker.gui;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration panel for entity properties in step 2 of the wizard
 */
public class EntityConfigurationPanel extends JPanel {
    
    private final String entityType;
    private final String entityName;
    
    // Configuration fields
    private JComboBox<String> factionCombo;
    private JComboBox<String> nationCombo;
    private JComboBox<String> weaponManagerCombo;
    private JSpinner healthSpinner;
    private JSpinner armorSpinner;
    private JSpinner ecmSpinner;
    private JSpinner speedSpinner;
    private JSpinner costSpinner;
    private JTextArea descriptionArea;
    
    public EntityConfigurationPanel(String entityType, String entityName) {
        this.entityType = entityType;
        this.entityName = entityName;
        
        initializePanel();
        setDefaultValues();
    }
    
    private void initializePanel() {
        setLayout(new BorderLayout());
        
        // Main configuration panel
        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Faction
        gbc.gridx = 0; gbc.gridy = 0;
        mainPanel.add(new JLabel("Faction:"), gbc);
        
        factionCombo = new JComboBox<>(new String[]{"Allied", "Soviet", "Neutral"});
        gbc.gridx = 1; gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        mainPanel.add(factionCombo, gbc);
        
        // Nation
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(new JLabel("Nation:"), gbc);
        
        nationCombo = new JComboBox<>(new String[]{
            "USA", "UK", "France", "West Germany", "Canada", "Denmark", "Norway",
            "USSR", "East Germany", "Poland", "Czechoslovakia", "Yugoslavia"
        });
        gbc.gridx = 1; gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        mainPanel.add(nationCombo, gbc);
        
        // Weapon Manager
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(new JLabel("Weapon Manager:"), gbc);
        
        weaponManagerCombo = new JComboBox<>(new String[]{
            "Generate New", "Use Existing Template", "No Weapons"
        });
        gbc.gridx = 1; gbc.gridy = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        mainPanel.add(weaponManagerCombo, gbc);
        
        // Health/HP
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(new JLabel("Health Points:"), gbc);
        
        healthSpinner = new JSpinner(new SpinnerNumberModel(100, 1, 10000, 10));
        gbc.gridx = 1; gbc.gridy = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        mainPanel.add(healthSpinner, gbc);
        
        // Armor
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(new JLabel("Armor:"), gbc);
        
        armorSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 20, 1));
        gbc.gridx = 1; gbc.gridy = 4;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        mainPanel.add(armorSpinner, gbc);
        
        // ECM
        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(new JLabel("ECM:"), gbc);
        
        ecmSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 10, 1));
        gbc.gridx = 1; gbc.gridy = 5;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        mainPanel.add(ecmSpinner, gbc);
        
        // Speed
        gbc.gridx = 0; gbc.gridy = 6; gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(new JLabel("Speed (km/h):"), gbc);
        
        speedSpinner = new JSpinner(new SpinnerNumberModel(50, 1, 1000, 5));
        gbc.gridx = 1; gbc.gridy = 6;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        mainPanel.add(speedSpinner, gbc);
        
        // Cost
        gbc.gridx = 0; gbc.gridy = 7; gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(new JLabel("Cost (points):"), gbc);
        
        costSpinner = new JSpinner(new SpinnerNumberModel(50, 1, 1000, 5));
        gbc.gridx = 1; gbc.gridy = 7;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        mainPanel.add(costSpinner, gbc);
        
        add(mainPanel, BorderLayout.NORTH);
        
        // Description area
        JPanel descPanel = new JPanel(new BorderLayout());
        descPanel.setBorder(BorderFactory.createTitledBorder("Description"));
        
        descriptionArea = new JTextArea(5, 30);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setText("Custom " + entityType.toLowerCase() + " unit created with the entity wizard.");
        
        JScrollPane scrollPane = new JScrollPane(descriptionArea);
        descPanel.add(scrollPane, BorderLayout.CENTER);
        
        add(descPanel, BorderLayout.CENTER);
        
        // Info panel
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        
        JTextArea infoArea = new JTextArea(
            "Configure the basic properties for your " + entityType.toLowerCase() + " unit.\n" +
            "These values will be used to generate appropriate templates and cross-references."
        );
        infoArea.setEditable(false);
        infoArea.setOpaque(false);
        infoArea.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        infoArea.setForeground(Color.GRAY);
        
        infoPanel.add(infoArea, BorderLayout.CENTER);
        add(infoPanel, BorderLayout.SOUTH);
    }
    
    private void setDefaultValues() {
        // Set entity-type-specific defaults
        switch (entityType) {
            case "Infantry":
                healthSpinner.setValue(50);
                armorSpinner.setValue(0);
                speedSpinner.setValue(25);
                costSpinner.setValue(15);
                weaponManagerCombo.setSelectedItem("Generate New");
                break;

            case "Tank":
                healthSpinner.setValue(300);
                armorSpinner.setValue(15);
                speedSpinner.setValue(60);
                costSpinner.setValue(150);
                weaponManagerCombo.setSelectedItem("Generate New");
                break;

            case "Artillery":
                healthSpinner.setValue(200);
                armorSpinner.setValue(5);
                speedSpinner.setValue(40);
                costSpinner.setValue(100);
                weaponManagerCombo.setSelectedItem("Generate New");
                break;

            case "Air Defense":
                healthSpinner.setValue(120);
                armorSpinner.setValue(3);
                speedSpinner.setValue(50);
                costSpinner.setValue(80);
                weaponManagerCombo.setSelectedItem("Generate New");
                break;

            case "Transport":
                healthSpinner.setValue(150);
                armorSpinner.setValue(3);
                speedSpinner.setValue(70);
                costSpinner.setValue(60);
                weaponManagerCombo.setSelectedItem("Use Existing Template");
                break;

            case "IFV":
                healthSpinner.setValue(180);
                armorSpinner.setValue(8);
                speedSpinner.setValue(65);
                costSpinner.setValue(90);
                weaponManagerCombo.setSelectedItem("Generate New");
                break;

            case "Command Vehicle":
            case "Command":
                healthSpinner.setValue(160);
                armorSpinner.setValue(5);
                speedSpinner.setValue(55);
                costSpinner.setValue(70);
                weaponManagerCombo.setSelectedItem("Use Existing Template");
                break;

            case "Reconnaissance":
                healthSpinner.setValue(80);
                armorSpinner.setValue(2);
                speedSpinner.setValue(80);
                costSpinner.setValue(40);
                weaponManagerCombo.setSelectedItem("Generate New");
                break;

            case "Supply":
            case "Logistics":
                healthSpinner.setValue(100);
                armorSpinner.setValue(1);
                speedSpinner.setValue(50);
                costSpinner.setValue(30);
                weaponManagerCombo.setSelectedItem("No Weapons");
                break;

            case "Aircraft":
            case "Fighter Aircraft":
            case "Bomber Aircraft":
                healthSpinner.setValue(150);
                armorSpinner.setValue(2);
                speedSpinner.setValue(800);
                costSpinner.setValue(200);
                weaponManagerCombo.setSelectedItem("Generate New");
                break;

            case "SEAD Aircraft":
                healthSpinner.setValue(140);
                armorSpinner.setValue(2);
                speedSpinner.setValue(750);
                costSpinner.setValue(180);
                weaponManagerCombo.setSelectedItem("Generate New");
                break;

            case "UAV":
                healthSpinner.setValue(60);
                armorSpinner.setValue(0);
                speedSpinner.setValue(400);
                costSpinner.setValue(80);
                weaponManagerCombo.setSelectedItem("Generate New");
                break;

            case "Helicopter":
            case "Command Helicopter":
                healthSpinner.setValue(100);
                armorSpinner.setValue(1);
                speedSpinner.setValue(300);
                costSpinner.setValue(120);
                weaponManagerCombo.setSelectedItem("Generate New");
                break;

            case "Anti-Tank":
                healthSpinner.setValue(90);
                armorSpinner.setValue(2);
                speedSpinner.setValue(45);
                costSpinner.setValue(70);
                weaponManagerCombo.setSelectedItem("Generate New");
                break;

            default:
                healthSpinner.setValue(100);
                armorSpinner.setValue(2);
                speedSpinner.setValue(50);
                costSpinner.setValue(50);
                weaponManagerCombo.setSelectedItem("Generate New");
                break;
        }
        
        // Set default faction based on common patterns
        factionCombo.setSelectedItem("Allied");
        nationCombo.setSelectedItem("USA");
    }
    
    public boolean validateConfiguration() {
        // Basic validation
        if ((Integer) healthSpinner.getValue() <= 0) {
            JOptionPane.showMessageDialog(this, "Health must be greater than 0.", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        
        if ((Integer) costSpinner.getValue() <= 0) {
            JOptionPane.showMessageDialog(this, "Cost must be greater than 0.", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        
        return true;
    }
    
    public Map<String, Object> getConfiguration() {
        Map<String, Object> config = new HashMap<>();
        
        config.put("Faction", factionCombo.getSelectedItem());
        config.put("Nation", nationCombo.getSelectedItem());
        config.put("WeaponManager", weaponManagerCombo.getSelectedItem());
        config.put("Health", healthSpinner.getValue());
        config.put("Armor", armorSpinner.getValue());
        config.put("ECM", ecmSpinner.getValue());
        config.put("Speed", speedSpinner.getValue());
        config.put("Cost", costSpinner.getValue());
        config.put("Description", descriptionArea.getText());
        
        return config;
    }
}
