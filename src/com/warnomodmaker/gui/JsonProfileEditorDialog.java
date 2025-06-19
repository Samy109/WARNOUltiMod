package com.warnomodmaker.gui;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Direct JSON Profile Editor Window
 * Separates metadata (_meta) from modification data (_input)
 * Allows creating new profiles by extending JSON
 */
public class JsonProfileEditorDialog extends JDialog {
    private JTextArea jsonTextArea;
    private JButton saveButton;
    private JButton loadButton;
    private JButton validateButton;
    private JButton newProfileButton;
    private JLabel statusLabel;
    private File currentFile;
    private boolean hasUnsavedChanges = false;

    public JsonProfileEditorDialog(Frame parent) {
        super(parent, "JSON Profile Editor", true);
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        
        // Handle window closing
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (confirmClose()) {
                    dispose();
                }
            }
        });
    }

    private void initializeComponents() {
        // JSON text area with syntax highlighting-like appearance
        jsonTextArea = new JTextArea(25, 80);
        jsonTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        jsonTextArea.setTabSize(2);
        jsonTextArea.setBackground(new Color(248, 248, 248));
        jsonTextArea.setForeground(Color.BLACK); // Ensure text is always black for readability
        jsonTextArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Track changes
        jsonTextArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { markAsChanged(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { markAsChanged(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { markAsChanged(); }
        });

        // Buttons
        newProfileButton = new JButton("New Profile Template");
        loadButton = new JButton("Load Profile");
        saveButton = new JButton("Save Profile");
        validateButton = new JButton("Validate JSON");
        
        // Status label
        statusLabel = new JLabel("Ready");
        statusLabel.setForeground(Color.BLUE);
    }

    private void setupLayout() {
        setLayout(new BorderLayout());

        // Top panel with buttons
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(newProfileButton);
        topPanel.add(loadButton);
        topPanel.add(saveButton);
        topPanel.add(validateButton);
        add(topPanel, BorderLayout.NORTH);

        // Center panel with JSON editor
        JScrollPane scrollPane = new JScrollPane(jsonTextArea);
        scrollPane.setBorder(new TitledBorder("JSON Profile Content"));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom panel with status
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomPanel.add(new JLabel("Status: "));
        bottomPanel.add(statusLabel);
        add(bottomPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(getParent());
    }

    private void setupEventHandlers() {
        newProfileButton.addActionListener(e -> createNewProfileTemplate());
        loadButton.addActionListener(e -> loadProfile());
        saveButton.addActionListener(e -> saveProfile());
        validateButton.addActionListener(e -> validateJson());
    }

    private void createNewProfileTemplate() {
        if (!confirmClose()) return;

        String template = createProfileTemplate();
        jsonTextArea.setText(template);
        currentFile = null;
        hasUnsavedChanges = true;
        updateTitle();
        setStatus("New profile template created", Color.GREEN);
    }

    private String createProfileTemplate() {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        
        return "{\n" +
               "  \"_meta\": {\n" +
               "    \"description\": \"Profile metadata and information\",\n" +
               "    \"author\": \"" + System.getProperty("user.name") + "\",\n" +
               "    \"created\": \"" + timestamp + "\",\n" +
               "    \"version\": \"1.0\",\n" +
               "    \"tags\": [\"custom\", \"modification\"]\n" +
               "  },\n" +
               "  \"_input\": {\n" +
               "    \"profileName\": \"New Custom Profile\",\n" +
               "    \"description\": \"Description of what this profile does\",\n" +
               "    \"gameVersion\": \"Current\",\n" +
               "    \"sourceFileName\": \"UniteDescriptor.ndf\",\n" +
               "    \"createdBy\": \"" + System.getProperty("user.name") + "\",\n" +
               "    \"createdDate\": \"" + timestamp + "\",\n" +
               "    \"lastModified\": \"" + timestamp + "\",\n" +
               "    \"modifications\": [\n" +
               "      {\n" +
               "        \"unitName\": \"Example_Unit_Name\",\n" +
               "        \"propertyPath\": \"ModulesDescriptors[*].MaxPhysicalDamages\",\n" +
               "        \"oldValue\": \"100\",\n" +
               "        \"oldValueType\": \"NUMBER\",\n" +
               "        \"newValue\": \"150\",\n" +
               "        \"newValueType\": \"NUMBER\",\n" +
               "        \"modificationType\": \"DIRECT_EDIT\",\n" +
               "        \"modificationDetails\": \"Increased health by 50%\"\n" +
               "      }\n" +
               "    ]\n" +
               "  }\n" +
               "}";
    }

    private void loadProfile() {
        if (!confirmClose()) return;

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON Profile Files", "json"));
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                String content = java.nio.file.Files.readString(file.toPath());
                jsonTextArea.setText(content);
                currentFile = file;
                hasUnsavedChanges = false;
                updateTitle();
                setStatus("Profile loaded successfully", Color.GREEN);
            } catch (IOException ex) {
                setStatus("Error loading profile: " + ex.getMessage(), Color.RED);
                JOptionPane.showMessageDialog(this, "Error loading profile:\n" + ex.getMessage(), 
                                            "Load Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveProfile() {
        if (currentFile == null) {
            saveAsProfile();
            return;
        }

        try {
            saveToFile(currentFile);
            hasUnsavedChanges = false;
            updateTitle();
            setStatus("Profile saved successfully", Color.GREEN);
        } catch (IOException ex) {
            setStatus("Error saving profile: " + ex.getMessage(), Color.RED);
            JOptionPane.showMessageDialog(this, "Error saving profile:\n" + ex.getMessage(), 
                                        "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveAsProfile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON Profile Files", "json"));
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().endsWith(".json")) {
                file = new File(file.getAbsolutePath() + ".json");
            }
            
            try {
                saveToFile(file);
                currentFile = file;
                hasUnsavedChanges = false;
                updateTitle();
                setStatus("Profile saved successfully", Color.GREEN);
            } catch (IOException ex) {
                setStatus("Error saving profile: " + ex.getMessage(), Color.RED);
                JOptionPane.showMessageDialog(this, "Error saving profile:\n" + ex.getMessage(), 
                                            "Save Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveToFile(File file) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(jsonTextArea.getText());
        }
    }

    private void validateJson() {
        String jsonText = jsonTextArea.getText().trim();
        if (jsonText.isEmpty()) {
            setStatus("No content to validate", Color.ORANGE);
            return;
        }

        try {
            // Basic JSON structure validation
            validateJsonStructure(jsonText);
            setStatus("JSON structure is valid", Color.GREEN);
        } catch (Exception ex) {
            setStatus("JSON validation error: " + ex.getMessage(), Color.RED);
            JOptionPane.showMessageDialog(this, "JSON Validation Error:\n" + ex.getMessage(), 
                                        "Validation Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void validateJsonStructure(String jsonText) throws Exception {
        // Flexible validation - support both new _meta/_input structure and legacy structure
        boolean hasMetaInputStructure = jsonText.contains("\"_meta\"") && jsonText.contains("\"_input\"");
        boolean hasLegacyStructure = jsonText.contains("\"profileName\"") || jsonText.contains("\"formatVersion\"");

        if (!hasMetaInputStructure && !hasLegacyStructure) {
            throw new Exception("Invalid profile structure. Must contain either _meta/_input sections or legacy profile fields");
        }

        // Check for modifications array (required in both structures)
        if (!jsonText.contains("\"modifications\"")) {
            throw new Exception("Missing required 'modifications' array");
        }
        
        // Check basic JSON syntax
        int braceCount = 0;
        int bracketCount = 0;
        boolean inString = false;
        boolean escaped = false;
        
        for (char c : jsonText.toCharArray()) {
            if (escaped) {
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                continue;
            }
            
            if (!inString) {
                if (c == '{') braceCount++;
                else if (c == '}') braceCount--;
                else if (c == '[') bracketCount++;
                else if (c == ']') bracketCount--;
            }
        }
        
        if (braceCount != 0) {
            throw new Exception("Mismatched braces { }");
        }
        if (bracketCount != 0) {
            throw new Exception("Mismatched brackets [ ]");
        }
    }

    private void markAsChanged() {
        if (!hasUnsavedChanges) {
            hasUnsavedChanges = true;
            updateTitle();
        }
    }

    private void updateTitle() {
        String title = "JSON Profile Editor";
        if (currentFile != null) {
            title += " - " + currentFile.getName();
        }
        if (hasUnsavedChanges) {
            title += " *";
        }
        setTitle(title);
    }

    private void setStatus(String message, Color color) {
        statusLabel.setText(message);
        statusLabel.setForeground(color);
    }

    private boolean confirmClose() {
        if (hasUnsavedChanges) {
            int result = JOptionPane.showConfirmDialog(this,
                "You have unsaved changes. Do you want to save before closing?",
                "Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION);
            
            if (result == JOptionPane.YES_OPTION) {
                saveProfile();
                return !hasUnsavedChanges; // Only close if save was successful
            } else if (result == JOptionPane.NO_OPTION) {
                return true; // Close without saving
            } else {
                return false; // Cancel close
            }
        }
        return true; // No unsaved changes, safe to close
    }
}
