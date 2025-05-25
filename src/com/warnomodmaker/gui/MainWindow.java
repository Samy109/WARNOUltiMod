package com.warnomodmaker.gui;

import com.warnomodmaker.model.NDFValue;
import com.warnomodmaker.model.ModificationTracker;
import com.warnomodmaker.model.ModProfile;
import com.warnomodmaker.model.UserPreferences;
import com.warnomodmaker.parser.NDFParser;
import com.warnomodmaker.parser.NDFWriter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.util.List;

/**
 * Main window for the WARNO Mod Maker application.
 */
public class MainWindow extends JFrame {
    private File currentFile;
    private List<NDFValue.ObjectValue> unitDescriptors;
    private NDFValue.NDFFileType currentFileType;
    private boolean modified;
    private NDFParser parser; // Store the parser for access to original tokens
    private ModificationTracker modificationTracker; // Track all modifications

    // GUI components
    private JPanel mainPanel;
    private JSplitPane splitPane;
    private com.warnomodmaker.gui.UnitBrowser unitBrowser;
    private com.warnomodmaker.gui.UnitEditor unitEditor;
    private JMenuBar menuBar;

    /**
     * Creates a new main window
     */
    public MainWindow() {
        // Set up the frame
        setTitle("WARNO Mod Maker");

        // Restore window position and size from preferences
        UserPreferences prefs = UserPreferences.getInstance();
        setSize(prefs.getWindowWidth(), prefs.getWindowHeight());
        setLocation(prefs.getWindowX(), prefs.getWindowY());

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        // Add window listener to handle close events
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitApplication();
            }
        });

        // Add component listener to save window position and size changes
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                // Save window position when moved
                UserPreferences.getInstance().saveWindowBounds(
                    getX(), getY(), getWidth(), getHeight()
                );
            }

            @Override
            public void componentResized(ComponentEvent e) {
                // Save window size when resized
                UserPreferences.getInstance().saveWindowBounds(
                    getX(), getY(), getWidth(), getHeight()
                );
            }
        });

        // Create the menu bar
        createMenuBar();

        // Create the main panel
        createMainPanel();

        // Initialize state
        currentFile = null;
        unitDescriptors = null;
        currentFileType = NDFValue.NDFFileType.UNKNOWN;
        modified = false;
        modificationTracker = new ModificationTracker();
        updateTitle();
    }

    /**
     * Creates the menu bar
     */
    private void createMenuBar() {
        menuBar = new JMenuBar();

        // File menu
        JMenu fileMenu = new JMenu("File");

        JMenuItem openItem = new JMenuItem("Open...");
        openItem.addActionListener(this::openFile);
        fileMenu.add(openItem);

        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.addActionListener(this::saveFile);
        fileMenu.add(saveItem);

        JMenuItem saveAsItem = new JMenuItem("Save As...");
        saveAsItem.addActionListener(this::saveFileAs);
        fileMenu.add(saveAsItem);

        fileMenu.addSeparator();

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> exitApplication());
        fileMenu.add(exitItem);

        menuBar.add(fileMenu);

        // Profile menu
        JMenu profileMenu = new JMenu("Profile");

        JMenuItem saveProfileItem = new JMenuItem("Save Profile As...");
        saveProfileItem.addActionListener(this::saveProfile);
        profileMenu.add(saveProfileItem);

        JMenuItem loadProfileItem = new JMenuItem("Load Profile...");
        loadProfileItem.addActionListener(this::loadProfile);
        profileMenu.add(loadProfileItem);

        profileMenu.addSeparator();

        JMenuItem viewProfileItem = new JMenuItem("View Current Profile...");
        viewProfileItem.addActionListener(this::viewProfile);
        profileMenu.add(viewProfileItem);

        JMenuItem clearProfileItem = new JMenuItem("Clear Profile");
        clearProfileItem.addActionListener(this::clearProfile);
        profileMenu.add(clearProfileItem);

        menuBar.add(profileMenu);

        // Tools menu
        JMenu toolsMenu = new JMenu("Tools");

        JMenuItem massModifyItem = new JMenuItem("Mass Modify...");
        massModifyItem.addActionListener(this::showMassModifyDialog);
        toolsMenu.add(massModifyItem);

        JMenuItem tagOrderEditorItem = new JMenuItem("Edit Tags & Orders...");
        tagOrderEditorItem.addActionListener(this::showTagAndOrderEditor);
        toolsMenu.add(tagOrderEditorItem);

        menuBar.add(toolsMenu);

        // Help menu
        JMenu helpMenu = new JMenu("Help");

        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(this::showAboutDialog);
        helpMenu.add(aboutItem);

        menuBar.add(helpMenu);

        setJMenuBar(menuBar);
    }

    /**
     * Creates the main panel
     */
    private void createMainPanel() {
        mainPanel = new JPanel(new BorderLayout());

        // Create the unit browser
        unitBrowser = new com.warnomodmaker.gui.UnitBrowser();
        unitBrowser.addUnitSelectionListener(this::unitSelected);

        // Create the unit editor
        unitEditor = new com.warnomodmaker.gui.UnitEditor();
        unitEditor.addPropertyChangeListener(e -> setModified(true));

        // Create the split pane
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, unitBrowser, unitEditor);
        splitPane.setDividerLocation(300);

        mainPanel.add(splitPane, BorderLayout.CENTER);

        setContentPane(mainPanel);
    }

    /**
     * Opens a file
     */
    private void openFile(ActionEvent e) {
        // Check if the current file has been modified
        if (modified) {
            int result = JOptionPane.showConfirmDialog(
                this,
                "The current file has been modified. Save changes?",
                "Save Changes",
                JOptionPane.YES_NO_CANCEL_OPTION
            );

            if (result == JOptionPane.YES_OPTION) {
                saveFile(null);
            } else if (result == JOptionPane.CANCEL_OPTION) {
                return;
            }
        }

        // Show file chooser starting from last used directory
        UserPreferences prefs = UserPreferences.getInstance();
        JFileChooser fileChooser = new JFileChooser(prefs.getLastDirectory());
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "NDF Files (*.ndf)", "ndf"
        ));

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();

            // Save the directory for next time
            prefs.setLastDirectory(file.getParent());

            try {
                // Clear any existing state before loading new file
                modificationTracker.clearModifications();
                unitEditor.setUnitDescriptor(null); // Clear editor first

                // Determine file type
                currentFileType = NDFValue.NDFFileType.fromFilename(file.getName());

                // Parse the file
                try (Reader reader = new BufferedReader(new FileReader(file))) {
                    parser = new NDFParser(reader); // Store the parser for later use
                    parser.setFileType(currentFileType); // Set the file type for proper parsing
                    unitDescriptors = parser.parse();
                }

                // Update the UI
                currentFile = file;
                modified = false;
                updateTitle();

                unitBrowser.setUnitDescriptors(unitDescriptors, currentFileType);

                String objectTypeName = currentFileType.getDisplayName().toLowerCase() + " descriptors";
                JOptionPane.showMessageDialog(
                    this,
                    "Loaded " + unitDescriptors.size() + " " + objectTypeName + ".",
                    "File Loaded",
                    JOptionPane.INFORMATION_MESSAGE
                );
            } catch (Exception ex) {
                String errorMessage = ex.getMessage();
                if (errorMessage == null || errorMessage.trim().isEmpty()) {
                    errorMessage = ex.getClass().getSimpleName() + " occurred during file loading";
                }

                JOptionPane.showMessageDialog(
                    this,
                    "Error opening file: " + errorMessage,
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                );
                ex.printStackTrace();
            }
        }
    }

    /**
     * Saves the current file
     */
    private void saveFile(ActionEvent e) {
        if (currentFile == null) {
            saveFileAs(e);
            return;
        }

        try {
            // Write the file
            try (Writer writer = new BufferedWriter(new FileWriter(currentFile))) {
                NDFWriter ndfWriter = new NDFWriter(writer);

                // Use exact formatting preservation if available
                if (parser != null) {
                    ndfWriter.setOriginalTokens(parser.getOriginalTokens());
                }

                ndfWriter.write(unitDescriptors);
            }

            // Update state
            modified = false;
            updateTitle();

            JOptionPane.showMessageDialog(
                this,
                "File saved successfully.",
                "File Saved",
                JOptionPane.INFORMATION_MESSAGE
            );
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                this,
                "Error saving file: " + ex.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
            ex.printStackTrace();
        }
    }

    /**
     * Saves the current file with a new name
     */
    private void saveFileAs(ActionEvent e) {
        // Show file chooser starting from last used directory
        UserPreferences prefs = UserPreferences.getInstance();
        JFileChooser fileChooser = new JFileChooser(prefs.getLastDirectory());
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "NDF Files (*.ndf)", "ndf"
        ));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();

            // Save the directory for next time
            prefs.setLastDirectory(file.getParent());

            // Add .ndf extension if not present
            if (!file.getName().toLowerCase().endsWith(".ndf")) {
                file = new File(file.getPath() + ".ndf");
            }

            // Check if file exists
            if (file.exists()) {
                int result = JOptionPane.showConfirmDialog(
                    this,
                    "The file already exists. Overwrite?",
                    "Overwrite File",
                    JOptionPane.YES_NO_OPTION
                );

                if (result != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            currentFile = file;
            saveFile(e);
        }
    }

    /**
     * Shows the mass modify dialog
     */
    private void showMassModifyDialog(ActionEvent e) {
        if (unitDescriptors == null || unitDescriptors.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "No units loaded. Please open a file first.",
                "No Units",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        com.warnomodmaker.gui.MassModifyDialog dialog = new com.warnomodmaker.gui.MassModifyDialog(this, unitDescriptors, modificationTracker, currentFileType);
        dialog.setVisible(true);

        if (dialog.isModified()) {
            setModified(true);
            unitBrowser.refresh();
            unitEditor.refresh();
        }
    }

    /**
     * Shows the tag and order editor dialog
     */
    private void showTagAndOrderEditor(ActionEvent e) {
        if (unitDescriptors == null || unitDescriptors.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "No units loaded. Please open a file first.",
                "No Units",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        com.warnomodmaker.gui.TagAndOrderEditorDialog dialog = new com.warnomodmaker.gui.TagAndOrderEditorDialog(this, unitDescriptors, modificationTracker);
        dialog.setVisible(true);

        if (dialog.isModified()) {
            setModified(true);
            unitBrowser.refresh();
            unitEditor.refresh();
        }
    }

    /**
     * Shows the about dialog
     */
    private void showAboutDialog(ActionEvent e) {
        JOptionPane.showMessageDialog(
            this,
            "WARNO Mod Maker\n" +
            "Version 1.0\n\n" +
            "A tool for modifying WARNO game files.",
            "About WARNO Mod Maker",
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    /**
     * Exits the application
     */
    private void exitApplication() {
        // Check if the current file has been modified
        if (modified) {
            int result = JOptionPane.showConfirmDialog(
                this,
                "The current file has been modified. Save changes?",
                "Save Changes",
                JOptionPane.YES_NO_CANCEL_OPTION
            );

            if (result == JOptionPane.YES_OPTION) {
                saveFile(null);
            } else if (result == JOptionPane.CANCEL_OPTION) {
                return;
            }
        }

        dispose();
        System.exit(0);
    }

    /**
     * Called when a unit is selected in the unit browser
     */
    private void unitSelected(NDFValue.ObjectValue unitDescriptor) {
        unitEditor.setUnitDescriptor(unitDescriptor, modificationTracker);
    }

    /**
     * Sets the modified flag and updates the title
     */
    private void setModified(boolean modified) {
        this.modified = modified;
        updateTitle();
    }

    /**
     * Updates the window title
     */
    private void updateTitle() {
        String title = "WARNO Mod Maker";

        if (currentFile != null) {
            title += " - " + currentFile.getName();
        }

        if (modified) {
            title += " *";
        }

        setTitle(title);
    }

    /**
     * Saves the current modification profile
     */
    private void saveProfile(ActionEvent e) {
        if (!modificationTracker.hasModifications()) {
            JOptionPane.showMessageDialog(
                this,
                "No modifications to save. Make some changes first.",
                "No Modifications",
                JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        // Get profile name from user
        String profileName = JOptionPane.showInputDialog(
            this,
            "Enter a name for this profile:",
            "Save Profile",
            JOptionPane.QUESTION_MESSAGE
        );

        if (profileName == null || profileName.trim().isEmpty()) {
            return; // User cancelled or entered empty name
        }

        // Show file chooser starting from last used directory
        UserPreferences prefs = UserPreferences.getInstance();
        JFileChooser fileChooser = new JFileChooser(prefs.getLastDirectory());
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "JSON Profile Files (*.json)", "json"
        ));
        fileChooser.setSelectedFile(new File(profileName + ".json"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();

            // Save the directory for next time
            prefs.setLastDirectory(file.getParent());

            // Add .json extension if not present
            if (!file.getName().toLowerCase().endsWith(".json")) {
                file = new File(file.getPath() + ".json");
            }

            try {
                String sourceFileName = currentFile != null ? currentFile.getName() : "Unknown";
                ModProfile profile = new ModProfile(profileName.trim(), modificationTracker, sourceFileName);
                profile.saveToFile(file);

                JOptionPane.showMessageDialog(
                    this,
                    String.format("Profile saved successfully!\n\nProfile: %s\nModifications: %d\nFile: %s",
                                profileName, modificationTracker.getModificationCount(), file.getName()),
                    "Profile Saved",
                    JOptionPane.INFORMATION_MESSAGE
                );
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                    this,
                    "Error saving profile: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                );
                ex.printStackTrace();
            }
        }
    }

    /**
     * Loads a modification profile
     */
    private void loadProfile(ActionEvent e) {
        if (unitDescriptors == null || unitDescriptors.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "No file loaded. Please open an NDF file first.",
                "No File Loaded",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        // Show file chooser starting from last used directory
        UserPreferences prefs = UserPreferences.getInstance();
        JFileChooser fileChooser = new JFileChooser(prefs.getLastDirectory());
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "JSON Profile Files (*.json)", "json"
        ));

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();

            // Save the directory for next time
            prefs.setLastDirectory(file.getParent());

            try {
                ModProfile profile = ModProfile.loadFromFile(file);

                // Show the profile load dialog with path validation
                ProfileLoadDialog loadDialog = new ProfileLoadDialog(this, profile, unitDescriptors, modificationTracker);
                loadDialog.setVisible(true);

                // Check if modifications were applied
                if (loadDialog.wasApplied()) {
                    setModified(true);
                    unitBrowser.refresh();
                    unitEditor.refresh();

                    JOptionPane.showMessageDialog(
                        this,
                        String.format("Profile '%s' loaded and applied successfully!", profile.getProfileName()),
                        "Profile Applied",
                        JOptionPane.INFORMATION_MESSAGE
                    );
                }

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                    this,
                    "Error loading profile: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                );
                ex.printStackTrace();
            }
        }
    }

    /**
     * Views the current modification profile
     */
    private void viewProfile(ActionEvent e) {
        if (!modificationTracker.hasModifications()) {
            JOptionPane.showMessageDialog(
                this,
                "No modifications in current profile.",
                "Empty Profile",
                JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        // TODO: Implement ProfileViewDialog
        // For now, show a simple summary
        ModificationTracker.ModificationStats stats = modificationTracker.getStats();
        StringBuilder message = new StringBuilder();
        message.append("Current Profile Summary:\n\n");
        message.append("Total Modifications: ").append(stats.totalModifications).append("\n");
        message.append("Units Modified: ").append(stats.uniqueUnits).append("\n");
        message.append("Properties Modified: ").append(stats.uniqueProperties).append("\n\n");
        message.append("Modification Types:\n");
        stats.modificationsByType.forEach((type, count) ->
            message.append("  ").append(type.getDisplayName()).append(": ").append(count).append("\n"));

        JOptionPane.showMessageDialog(
            this,
            message.toString(),
            "Current Profile",
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    /**
     * Clears the current modification profile
     */
    private void clearProfile(ActionEvent e) {
        if (!modificationTracker.hasModifications()) {
            JOptionPane.showMessageDialog(
                this,
                "No modifications to clear.",
                "Empty Profile",
                JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        int result = JOptionPane.showConfirmDialog(
            this,
            String.format("Clear all %d modifications from the current profile?\n\nThis action cannot be undone.",
                        modificationTracker.getModificationCount()),
            "Clear Profile",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );

        if (result == JOptionPane.YES_OPTION) {
            modificationTracker.clearModifications();
            JOptionPane.showMessageDialog(
                this,
                "Profile cleared successfully.",
                "Profile Cleared",
                JOptionPane.INFORMATION_MESSAGE
            );
        }
    }
}
