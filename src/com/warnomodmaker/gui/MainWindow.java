package com.warnomodmaker.gui;

import com.warnomodmaker.gui.components.StatusBar;
import com.warnomodmaker.gui.components.EnhancedTabbedPane;
import com.warnomodmaker.model.FileTabState;
import com.warnomodmaker.model.NDFValue;
import com.warnomodmaker.model.ModificationTracker;
import com.warnomodmaker.model.ModificationRecord;
import com.warnomodmaker.model.ModProfile;
import com.warnomodmaker.model.PropertyScanner;
import com.warnomodmaker.model.PropertyUpdater;
import com.warnomodmaker.model.UserPreferences;
import com.warnomodmaker.model.CrossSystemIntegrityManager;
import com.warnomodmaker.model.PropertyPathMigrationManager;
import com.warnomodmaker.parser.NDFParser;
import com.warnomodmaker.parser.NDFWriter;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainWindow extends JFrame implements FileLoader {

    private List<FileTabState> tabStates;
    private EnhancedTabbedPane tabbedPane;
    private int nextTabId = 1;

    private JPanel mainPanel;
    private JMenuBar menuBar;
    private StatusBar statusBar;

    // Cross-system integrity management
    private CrossSystemIntegrityManager integrityManager;

    public MainWindow() {
        setTitle("WARNO Mod Maker");

        // Restore window position and size from preferences with bounds checking
        UserPreferences prefs = UserPreferences.getInstance();

        // Get screen dimensions for bounds checking
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

        // Restore size with reasonable limits
        int width = Math.min(prefs.getWindowWidth(), (int)(screenSize.width * 0.9));
        int height = Math.min(prefs.getWindowHeight(), (int)(screenSize.height * 0.9));
        width = Math.max(width, 800); // Minimum width
        height = Math.max(height, 600); // Minimum height

        // Restore position with bounds checking
        int x = Math.max(0, Math.min(prefs.getWindowX(), screenSize.width - width));
        int y = Math.max(0, Math.min(prefs.getWindowY(), screenSize.height - height));

        setSize(width, height);
        setLocation(x, y);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitApplication();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                if (statusBar != null) {
                    statusBar.dispose();
                }
            }
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                // Only save bounds if window is not maximized
                if (getExtendedState() != JFrame.MAXIMIZED_BOTH) {
                    UserPreferences.getInstance().saveWindowBounds(
                        getX(), getY(), getWidth(), getHeight()
                    );
                }
            }

            @Override
            public void componentResized(ComponentEvent e) {
                // Only save bounds if window is not maximized
                if (getExtendedState() != JFrame.MAXIMIZED_BOTH) {
                    UserPreferences.getInstance().saveWindowBounds(
                        getX(), getY(), getWidth(), getHeight()
                    );
                }
            }
        });
        tabStates = new ArrayList<>();

        // Initialize cross-system integrity manager
        integrityManager = new CrossSystemIntegrityManager();

        createMenuBar();
        createStatusBar();
        createMainPanel();
        updateTitle();
    }


    private void createMenuBar() {
        menuBar = new JMenuBar();

        // File menu
        JMenu fileMenu = new JMenu("File");

        JMenuItem openItem = new JMenuItem("Open...");
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        openItem.addActionListener(this::openFile);
        fileMenu.add(openItem);

        fileMenu.addSeparator();

        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        saveItem.addActionListener(this::saveFile);
        fileMenu.add(saveItem);

        JMenuItem saveAsItem = new JMenuItem("Save As...");
        saveAsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        saveAsItem.addActionListener(this::saveFileAs);
        fileMenu.add(saveAsItem);

        fileMenu.addSeparator();

        JMenuItem closeTabItem = new JMenuItem("Close Tab");
        closeTabItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK));
        closeTabItem.addActionListener(this::closeCurrentTab);
        fileMenu.add(closeTabItem);

        JMenuItem closeAllTabsItem = new JMenuItem("Close All Tabs");
        closeAllTabsItem.addActionListener(this::closeAllTabs);
        fileMenu.add(closeAllTabsItem);

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

        // SPYBORG ENHANCEMENT: JSON Profile Editor
        JMenuItem jsonEditorItem = new JMenuItem("JSON Profile Editor...");
        jsonEditorItem.addActionListener(this::showJsonProfileEditor);
        profileMenu.add(jsonEditorItem);

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

        JMenuItem manualListItem = new JMenuItem("Create Manual List...");
        manualListItem.addActionListener(this::showManualListDialog);
        toolsMenu.add(manualListItem);



        toolsMenu.addSeparator();

        JMenuItem additiveOpsItem = new JMenuItem("Additive Operations...");
        additiveOpsItem.addActionListener(this::showAdditiveOperationsDialog);
        toolsMenu.add(additiveOpsItem);

        toolsMenu.addSeparator();

        JMenuItem entityCreationItem = new JMenuItem("Create Complete Entity...");
        entityCreationItem.addActionListener(this::showEntityCreationDialog);
        toolsMenu.add(entityCreationItem);

        menuBar.add(toolsMenu);

        // Help menu
        JMenu helpMenu = new JMenu("Help");

        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(this::showAboutDialog);
        helpMenu.add(aboutItem);

        menuBar.add(helpMenu);

        setJMenuBar(menuBar);
    }





    private void createStatusBar() {
        statusBar = new StatusBar();
    }


    private void createMainPanel() {
        mainPanel = new JPanel(new BorderLayout());
        tabbedPane = new EnhancedTabbedPane();
        tabbedPane.addChangeListener(this::onTabChanged);

        // Listen for tab close events
        tabbedPane.addPropertyChangeListener("tabClose", e -> {
            int tabIndex = (Integer) e.getNewValue();
            closeTab(tabIndex);
        });
        tabbedPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isMiddleMouseButton(e)) {
                    int tabIndex = tabbedPane.indexAtLocation(e.getX(), e.getY());
                    if (tabIndex >= 0) {
                        closeTab(tabIndex);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int tabIndex = tabbedPane.indexAtLocation(e.getX(), e.getY());
                    if (tabIndex >= 0) {
                        showTabContextMenu(e, tabIndex);
                    }
                }
            }
        });
        setupTabKeyBindings();

        // Add components to main panel
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        mainPanel.add(statusBar, BorderLayout.SOUTH);

        showWelcomeTab();

        setContentPane(mainPanel);
    }


    private void openFile(ActionEvent e) {
        UserPreferences prefs = UserPreferences.getInstance();
        JFileChooser fileChooser = new JFileChooser(prefs.getLastNDFDirectory());
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "NDF Files (*.ndf)", "ndf"
        ));

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            prefs.setLastNDFDirectory(file.getParent());

            // Check if file is already open
            for (int i = 0; i < tabStates.size(); i++) {
                FileTabState tabState = tabStates.get(i);
                if (tabState.getFile() != null && tabState.getFile().equals(file)) {
                    // File already open - check if it has been modified
                    if (tabState.isModified()) {
                        String fileName = tabState.getFile() != null ? tabState.getFile().getName() : "Untitled";
                        int result = JOptionPane.showConfirmDialog(
                            this,
                            "The file '" + fileName + "' has been modified. Save changes before editing?",
                            "Save Changes",
                            JOptionPane.YES_NO_CANCEL_OPTION
                        );

                        if (result == JOptionPane.YES_OPTION) {
                            // Save the file first, then reload fresh
                            saveTabToFile(tabState, tabState.getFile());
                            reloadTabFromDisk(i, file);
                        } else if (result == JOptionPane.NO_OPTION) {
                            // Don't save, just reload fresh
                            reloadTabFromDisk(i, file);
                        } else {
                            // Cancel - just switch to the existing tab
                            tabbedPane.setSelectedIndex(i);
                        }
                    } else {
                        // File not modified, just switch to that tab
                        tabbedPane.setSelectedIndex(i);
                    }
                    return;
                }
            }
            loadFileInBackground(file);
        }
    }


    private void saveFile(ActionEvent e) {
        FileTabState currentTab = getCurrentTabState();
        if (currentTab == null || !currentTab.hasData()) {
            JOptionPane.showMessageDialog(
                this,
                "No file to save. Please open a file first.",
                "No File",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        if (currentTab.getFile() == null) {
            saveFileAs(e);
            return;
        }

        saveTabToFile(currentTab, currentTab.getFile());
    }


    private void saveFileAs(ActionEvent e) {
        FileTabState currentTab = getCurrentTabState();
        if (currentTab == null || !currentTab.hasData()) {
            JOptionPane.showMessageDialog(
                this,
                "No file to save. Please open a file first.",
                "No File",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        UserPreferences prefs = UserPreferences.getInstance();
        JFileChooser fileChooser = new JFileChooser(prefs.getLastNDFDirectory());
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "NDF Files (*.ndf)", "ndf"
        ));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            prefs.setLastNDFDirectory(file.getParent());
            if (!file.getName().toLowerCase().endsWith(".ndf")) {
                file = new File(file.getPath() + ".ndf");
            }
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
            currentTab.setFile(file);
            saveTabToFile(currentTab, file);
        }
    }


    private void showMassModifyDialog(ActionEvent e) {
        FileTabState currentTab = getCurrentTabState();
        if (currentTab == null || !currentTab.hasData()) {
            JOptionPane.showMessageDialog(
                this,
                "No units loaded. Please open a file first.",
                "No Units",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        com.warnomodmaker.gui.MassModifyDialog dialog = new com.warnomodmaker.gui.MassModifyDialog(
            this, currentTab.getUnitDescriptors(), currentTab.getModificationTracker(), currentTab.getFileType());
        dialog.setVisible(true);

        if (dialog.isModified()) {
            currentTab.setModified(true);
            refreshCurrentTabWithProgress();
            updateTitle();
        }
    }

    private void showManualListDialog(ActionEvent e) {
        FileTabState currentTab = getCurrentTabState();
        if (currentTab == null || !currentTab.hasData()) {
            JOptionPane.showMessageDialog(
                this,
                "No units loaded. Please open a file first.",
                "No Units",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        ManualListDialog dialog = new ManualListDialog(this, currentTab.getUnitDescriptors());
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            List<NDFValue.ObjectValue> selectedUnits = dialog.getSelectedUnits();
            if (!selectedUnits.isEmpty()) {
                // Open mass modify dialog with the selected units
                MassModifyDialog massModifyDialog = new MassModifyDialog(
                    this, selectedUnits, currentTab.getModificationTracker(), currentTab.getFileType());
                massModifyDialog.setVisible(true);

                if (massModifyDialog.isModified()) {
                    currentTab.setModified(true);
                    refreshCurrentTabWithProgress();
                    updateTitle();
                }
            }
        }
    }







    private void showAdditiveOperationsDialog(ActionEvent e) {
        FileTabState currentTab = getCurrentTabState();
        if (currentTab == null || !currentTab.hasData()) {
            JOptionPane.showMessageDialog(
                this,
                "No file loaded. Please open a file first.",
                "No File",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        // Use the generic method to get NDF objects for all file types
        List<NDFValue.ObjectValue> objects = currentTab.getNDFObjects();
        if (objects == null) {
            JOptionPane.showMessageDialog(
                this,
                "File data is not available. Please try reloading the file.",
                "No Data",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        try {
            AdditiveOperationsDialog dialog = new AdditiveOperationsDialog(
                this, objects, currentTab.getFileType(), currentTab.getModificationTracker());
            dialog.setVisible(true);

            if (dialog.wasOperationPerformed()) {
                currentTab.setModified(true);

                // Refresh all UI components to show newly added objects
                refreshCurrentTab();
                updateTitle();

                // Force refresh of any open modification dialogs
                refreshModificationDialogs();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                this,
                "Error opening additive operations dialog: " + ex.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
            ex.printStackTrace();
        }
    }

    private void showEntityCreationDialog(ActionEvent e) {
        // Require at least one file to be open first
        if (tabStates.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "No files are currently open. Please open at least one NDF file first to establish the working directory.",
                "No Files Open",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        try {
            EntityCreationWizard wizard = new EntityCreationWizard(this, integrityManager, this);
            wizard.setVisible(true);

            if (wizard.wasEntityCreated()) {
                // CRITICAL: Re-register all files with integrity manager to update cross-file tracking
                for (FileTabState tabState : tabStates) {
                    if (tabState.hasData() && tabState.getFile() != null) {
                        integrityManager.registerFile(tabState.getFile().getName(),
                                                    tabState.getFileType(),
                                                    tabState.getUnitDescriptors());
                    }
                    tabState.setModified(true);
                }

                // Switch to the first tab (usually UniteDescriptorOLD.ndf where the new entity was created)
                if (!tabStates.isEmpty()) {
                    tabbedPane.setSelectedIndex(0);
                }

                // Refresh the current tab and highlight the new entity
                refreshCurrentTab();

                String entityName = wizard.getCreatedEntityName();
                highlightNewEntity(entityName);
                updateTitle();

                JOptionPane.showMessageDialog(
                    this,
                    "Entity '" + wizard.getCreatedEntityName() + "' (" + wizard.getCreatedEntityType() +
                    ") created successfully!\nAll affected files have been marked as modified.",
                    "Entity Created",
                    JOptionPane.INFORMATION_MESSAGE
                );
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                this,
                "Error opening entity creation wizard: " + ex.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
            ex.printStackTrace();
        }
    }

    /**
     * Auto-load required files for entity creation
     */
    public void autoLoadRequiredFiles(String[] fileNames) {
        // Require at least one file to be open first
        if (tabStates.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No files are currently open. Please open at least one NDF file first to establish the working directory.",
                "No Files Open",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Find the root directory that contains both main NDF files and GameData folder
        File directory = null;
        for (FileTabState tabState : tabStates) {
            if (tabState.getFile() != null) {
                File fileDir = tabState.getFile().getParentFile();
                // Walk up the directory tree to find the root that contains GameData
                directory = findRootDirectory(fileDir);
                if (directory != null) {
                    break;
                }
            }
        }

        // If we can't find a good directory, try the tester files directory as fallback
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            // Try tester files directory as fallback
            File testerDir = new File("tester files");
            if (testerDir.exists() && testerDir.isDirectory()) {
                directory = testerDir;
                System.out.println("Using tester files directory as fallback");
            } else {
                JOptionPane.showMessageDialog(this,
                    "Cannot determine working directory from open files. Please open a file manually first.",
                    "Directory Not Found",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        // Debug: Show what directory we're using
        System.out.println("Auto-load using directory: " + directory.getAbsolutePath());

        StringBuilder results = new StringBuilder();
        results.append("Auto-loading results:\n\n");

        int loaded = 0;
        int alreadyOpen = 0;
        int notFound = 0;

        for (String fileName : fileNames) {
            // Extract just the filename for comparison (in case of paths like GameData/Generated/Sound/SoundDescriptors.ndf)
            String baseFileName = new File(fileName).getName();

            // Check if file is already open
            boolean isAlreadyOpen = false;
            for (FileTabState tabState : tabStates) {
                if (tabState.getFile() != null && tabState.getFile().getName().equals(baseFileName)) {
                    isAlreadyOpen = true;
                    break;
                }
            }

            if (isAlreadyOpen) {
                results.append("[OK] ").append(fileName).append(" (already open)\n");
                alreadyOpen++;
                continue;
            }

            // Try to find and load the file using smart location logic
            File file = findFileInWarnoStructure(directory, fileName);
            System.out.println("Looking for: " + fileName + " at: " + (file != null ? file.getAbsolutePath() : "null") + " (exists: " + (file != null && file.exists()) + ")");

            // Extract just the filename for display (remove any path components)
            String displayName = new File(fileName).getName();

            if (file != null && file.exists() && file.isFile()) {
                try {
                    loadFileInBackgroundSilent(file);
                    results.append("[OK] ").append(displayName).append(" (loaded)\n");
                    loaded++;
                } catch (Exception e) {
                    results.append("[ERROR] ").append(displayName).append(" (error: ").append(e.getMessage()).append(")\n");
                    notFound++;
                }
            } else {
                results.append("[FAIL] ").append(displayName).append(" (not found)\n");
                notFound++;
            }
        }

        results.append("\nSummary: ").append(loaded).append(" loaded, ")
                .append(alreadyOpen).append(" already open, ")
                .append(notFound).append(" not found/failed");

        JOptionPane.showMessageDialog(this,
            results.toString(),
            "Auto-Load Results",
            JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public Map<String, List<NDFValue.ObjectValue>> getOpenFiles() {
        Map<String, List<NDFValue.ObjectValue>> openFiles = new HashMap<>();

        for (FileTabState tabState : tabStates) {
            if (tabState.hasData() && tabState.getFile() != null) {
                String fileName = tabState.getFile().getName();
                List<NDFValue.ObjectValue> objects = tabState.getNDFObjects();
                openFiles.put(fileName, objects);
            }
        }
        return openFiles;
    }

    @Override
    public Map<String, ModificationTracker> getModificationTrackers() {
        Map<String, ModificationTracker> trackers = new HashMap<>();

        for (FileTabState tabState : tabStates) {
            if (tabState.hasData() && tabState.getFile() != null) {
                String fileName = tabState.getFile().getName();
                trackers.put(fileName, tabState.getModificationTracker());
            }
        }

        return trackers;
    }

    /**
     * Find a file in the directory tree, looking both in the specified directory
     * and in parent/sibling directories for files with subdirectory paths
     */
    private File findFileInDirectoryTree(File baseDirectory, String fileName) {
        // Try direct path first
        File directFile = new File(baseDirectory, fileName);
        if (directFile.exists()) {
            return directFile;
        }

        // If fileName contains path separators, try more search strategies
        if (fileName.contains("/") || fileName.contains("\\")) {
            // Try looking in parent directory
            File parentDir = baseDirectory.getParentFile();
            if (parentDir != null) {
                File parentFile = new File(parentDir, fileName);
                if (parentFile.exists()) {
                    return parentFile;
                }
            }

            // Try recursive search in subdirectories
            String targetFileName = new File(fileName).getName();
            File foundFile = searchFileRecursively(baseDirectory, targetFileName);
            if (foundFile != null) {
                return foundFile;
            }

            // Try searching from parent directory
            if (parentDir != null) {
                foundFile = searchFileRecursively(parentDir, targetFileName);
                if (foundFile != null) {
                    return foundFile;
                }
            }
        }

        return null;
    }

    /**
     * Recursively search for a file by name in directory tree
     */
    private File searchFileRecursively(File directory, String fileName) {
        if (!directory.exists() || !directory.isDirectory()) {
            return null;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return null;
        }

        // First check direct children
        for (File file : files) {
            if (file.isFile() && file.getName().equals(fileName)) {
                return file;
            }
        }

        // Then search subdirectories (limit depth to avoid infinite recursion)
        for (File file : files) {
            if (file.isDirectory() && !file.getName().startsWith(".")) {
                File found = searchFileRecursively(file, fileName);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    /**
     * Find the root directory that contains both main NDF files and GameData folder
     */
    private File findRootDirectory(File startDir) {
        File current = startDir;

        // Walk up the directory tree looking for a directory that contains GameData
        while (current != null) {
            File gameDataDir = new File(current, "GameData");
            if (gameDataDir.exists() && gameDataDir.isDirectory()) {
                // This looks like the root directory
                return current;
            }
            current = current.getParentFile();
        }

        // If we can't find GameData, just use the original directory
        return startDir;
    }

    /**
     * Find a file in the WARNO directory structure, knowing where different file types are located
     */
    private File findFileInWarnoStructure(File rootDir, String fileName) {
        // Handle files with subdirectory paths (like GameData/Generated/Sound/SoundDescriptors.ndf)
        if (fileName.contains("/") || fileName.contains("\\")) {
            File directPath = new File(rootDir, fileName);
            System.out.println("  Trying subdirectory path: " + directPath.getAbsolutePath() + " (exists: " + directPath.exists() + ")");
            if (directPath.exists()) {
                return directPath;
            }
        } else {
            // For simple filenames, try the common WARNO locations
            String[] commonLocations = {
                // FIRST: Direct in root directory (most important for files like GeneratedDepictionInfantry.ndf)
                fileName,
                // Then try subdirectories where most files are found
                "GameData/Generated/Gameplay/Gfx/" + fileName,
                "GameData/Generated/Gameplay/Gfx/Infanterie/" + fileName,  // Infantry files are in Infanterie subdirectory
                "GameData/Generated/Gameplay/" + fileName,
                "GameData/Gameplay/Gfx/" + fileName,
                "GameData/Generated/" + fileName,
                "GameData/" + fileName,
                // Additional locations for generated depiction files
                "GameData/Generated/Gfx/" + fileName,
                "GameData/Gfx/" + fileName,
                "Generated/" + fileName,
                "Gfx/" + fileName
            };

            for (String location : commonLocations) {
                File file = new File(rootDir, location);
                System.out.println("  Trying: " + file.getAbsolutePath() + " (exists: " + file.exists() + ")");
                if (file.exists() && file.isFile()) {
                    return file;
                }
            }
        }

        return null;
    }

    private String getFileTypeName(NDFValue.NDFFileType fileType) {
        switch (fileType) {
            case UNITE_DESCRIPTOR: return "UniteDescriptor";
            case AMMUNITION: return "Ammunition";
            case AMMUNITION_MISSILES: return "AmmunitionMissiles";
            case WEAPON_DESCRIPTOR: return "WeaponDescriptor";
            case BUILDING_DESCRIPTORS: return "BuildingDescriptors";
            case MISSILE_DESCRIPTORS: return "MissileDescriptors";

            // Comprehensive dependency support
            case GENERATED_INFANTRY_DEPICTION: return "GeneratedInfantryDepiction";
            case VEHICLE_DEPICTION: return "VehicleDepiction";
            case AIRCRAFT_DEPICTION: return "AircraftDepiction";
            case DEPICTION_DESCRIPTOR: return "DepictionDescriptor";
            case NDF_DEPICTION_LIST: return "NdfDepictionList";

            case EFFETS_SUR_UNITE: return "EffetsSurUnite";
            case DAMAGE_RESISTANCE: return "DamageResistance";
            case DAMAGE_RESISTANCE_FAMILY_LIST: return "DamageResistanceFamilyList";
            case EXPERIENCE_LEVELS: return "ExperienceLevels";
            case PROJECTILE_TYPE: return "ProjectileType";
            case ARTILLERY_PROJECTILE_DESCRIPTOR: return "ArtilleryProjectileDescriptor";

            case SOUND_DESCRIPTORS: return "SoundDescriptors";
            case WEAPON_SOUND_HAPPENINGS: return "WeaponSoundHappenings";
            case VEHICLE_SOUND_DESCRIPTOR: return "VehicleSoundDescriptor";

            case INFANTRY_ANIMATION_DESCRIPTOR: return "InfantryAnimationDescriptor";
            case VEHICLE_ANIMATION_DESCRIPTOR: return "VehicleAnimationDescriptor";
            case AIRCRAFT_ANIMATION_DESCRIPTOR: return "AircraftAnimationDescriptor";


            case SUPPLY_DESCRIPTOR: return "SupplyDescriptor";
            case RECON_DESCRIPTOR: return "ReconDescriptor";
            case COMMAND_DESCRIPTOR: return "CommandDescriptor";

            default: return "Unknown";
        }
    }


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





    private void exitApplication() {
        List<FileTabState> modifiedTabs = new ArrayList<>();
        for (FileTabState tabState : tabStates) {
            if (tabState.isModified()) {
                modifiedTabs.add(tabState);
            }
        }

        if (!modifiedTabs.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append("The following files have been modified:\n\n");
            for (FileTabState tabState : modifiedTabs) {
                String fileName = tabState.getFile() != null ? tabState.getFile().getName() : "Untitled";
                message.append("- ").append(fileName).append("\n");
            }
            message.append("\nSave changes before exiting?");

            int result = JOptionPane.showConfirmDialog(
                this,
                message.toString(),
                "Save Changes",
                JOptionPane.YES_NO_CANCEL_OPTION
            );

            if (result == JOptionPane.YES_OPTION) {
                for (FileTabState tabState : modifiedTabs) {
                    if (tabState.getFile() != null) {
                        saveTabToFile(tabState, tabState.getFile());
                    } else {
                        // Would need to prompt for save location for each untitled tab
                        // For now, skip untitled tabs
                    }
                }
            } else if (result == JOptionPane.CANCEL_OPTION) {
                return;
            }
        }

        dispose();
        System.exit(0);
    }


    private void updateTitle() {
        String title = "WARNO Mod Maker";

        FileTabState currentTab = getCurrentTabState();
        if (currentTab != null && currentTab.getFile() != null) {
            title += " - " + currentTab.getFile().getName();
            if (currentTab.isModified()) {
                title += " *";
            }
        }
        if (tabStates.size() > 1) {
            title += " (" + tabStates.size() + " files)";
        }

        setTitle(title);
    }


    private void saveProfile(ActionEvent e) {
        FileTabState currentTab = getCurrentTabState();
        if (currentTab == null || !currentTab.getModificationTracker().hasModifications()) {
            JOptionPane.showMessageDialog(
                this,
                "No modifications to save. Make some changes first.",
                "No Modifications",
                JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }
        String profileName = JOptionPane.showInputDialog(
            this,
            "Enter a name for this profile:",
            "Save Profile",
            JOptionPane.QUESTION_MESSAGE
        );

        if (profileName == null || profileName.trim().isEmpty()) {
            return; // User cancelled or entered empty name
        }
        UserPreferences prefs = UserPreferences.getInstance();
        JFileChooser fileChooser = new JFileChooser(prefs.getLastProfileDirectory());
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "JSON Profile Files (*.json)", "json"
        ));
        fileChooser.setSelectedFile(new File(profileName + ".json"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            prefs.setLastProfileDirectory(file.getParent());
            if (!file.getName().toLowerCase().endsWith(".json")) {
                file = new File(file.getPath() + ".json");
            }

            try {
                String sourceFileName = currentTab.getFile() != null ? currentTab.getFile().getName() : "Unknown";
                ModProfile profile = new ModProfile(profileName.trim(), currentTab.getModificationTracker(), sourceFileName);
                profile.saveToFile(file);

                JOptionPane.showMessageDialog(
                    this,
                    String.format("Profile saved successfully!\n\nProfile: %s\nModifications: %d\nFile: %s",
                                profileName, currentTab.getModificationTracker().getModificationCount(), file.getName()),
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


    private void loadProfile(ActionEvent e) {
        FileTabState currentTab = getCurrentTabState();
        if (currentTab == null || !currentTab.hasData()) {
            JOptionPane.showMessageDialog(
                this,
                "No file loaded. Please open an NDF file first.",
                "No File Loaded",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        UserPreferences prefs = UserPreferences.getInstance();
        JFileChooser fileChooser = new JFileChooser(prefs.getLastProfileDirectory());
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "JSON Profile Files (*.json)", "json"
        ));

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            prefs.setLastProfileDirectory(file.getParent());

            try {
                ModProfile profile = ModProfile.loadFromFile(file);

                // Silently attempt to migrate profile paths in background
                integrityManager.migrateModProfile(profile);

                ProfileLoadDialog loadDialog = new ProfileLoadDialog(this, profile, currentTab.getUnitDescriptors(), currentTab.getModificationTracker());
                loadDialog.setVisible(true);
                if (loadDialog.wasApplied()) {
                    currentTab.setModified(true);
                    refreshCurrentTab();
                    updateTitle();

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


    private void viewProfile(ActionEvent e) {
        FileTabState currentTab = getCurrentTabState();
        if (currentTab == null || !currentTab.getModificationTracker().hasModifications()) {
            JOptionPane.showMessageDialog(
                this,
                "No modifications in current profile.",
                "Empty Profile",
                JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }
        // For now, show a simple summary
        ModificationTracker.ModificationStats stats = currentTab.getModificationTracker().getStats();
        StringBuilder message = new StringBuilder();
        message.append("Current Profile Summary:\n\n");
        String fileName = currentTab.getFile() != null ? currentTab.getFile().getName() : "Untitled";
        message.append("File: ").append(fileName).append("\n");
        message.append("Total Modifications: ").append(stats.totalModifications).append("\n");
        message.append("Units Modified: ").append(stats.uniqueUnits).append("\n");
        message.append("Properties Modified: ").append(stats.uniqueProperties).append("\n\n");
        message.append("Modification Types:\n");
        stats.modificationsByType.forEach((type, count) ->
            message.append("  ").append(type.getDisplayName()).append(": ").append(count).append("\n"));

        JOptionPane.showMessageDialog(
            this,
            message.toString(),
            "Current Profile - " + fileName,
            JOptionPane.INFORMATION_MESSAGE
        );
    }


    private void clearProfile(ActionEvent e) {
        FileTabState currentTab = getCurrentTabState();
        if (currentTab == null || !currentTab.getModificationTracker().hasModifications()) {
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
                        currentTab.getModificationTracker().getModificationCount()),
            "Clear Profile",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );

        if (result == JOptionPane.YES_OPTION) {
            currentTab.clearModifications();
            refreshCurrentTab();
            updateTitle();
            JOptionPane.showMessageDialog(
                this,
                "Profile cleared successfully.",
                "Profile Cleared",
                JOptionPane.INFORMATION_MESSAGE
            );
        }
    }

    /**
     * SPYBORG ENHANCEMENT: Show JSON Profile Editor for direct profile editing
     */
    private void showJsonProfileEditor(ActionEvent e) {
        JsonProfileEditorDialog editor = new JsonProfileEditorDialog(this);
        editor.setVisible(true);
    }


    private FileTabState getCurrentTabState() {
        int selectedIndex = tabbedPane.getSelectedIndex();
        if (selectedIndex >= 0 && selectedIndex < tabStates.size()) {
            return tabStates.get(selectedIndex);
        }
        return null;
    }

    public NDFValue.NDFFileType getCurrentFileType() {
        FileTabState currentTab = getCurrentTabState();
        return currentTab != null ? currentTab.getFileType() : NDFValue.NDFFileType.UNKNOWN;
    }


    private FileTabPanel getCurrentTabPanel() {
        int selectedIndex = tabbedPane.getSelectedIndex();
        if (selectedIndex >= 0) {
            Component component = tabbedPane.getComponentAt(selectedIndex);
            if (component instanceof FileTabPanel) {
                return (FileTabPanel) component;
            }
        }
        return null;
    }


    private void createNewTab(File file, List<NDFValue.ObjectValue> ndfObjects,
                             NDFValue.NDFFileType fileType, NDFParser parser) {
        FileTabState tabState = new FileTabState(file, ndfObjects, fileType, parser);
        FileTabPanel tabPanel = new FileTabPanel(tabState);
        tabPanel.addModificationListener(e -> {
            tabState.setModified(true);
            updateTabTitle(tabState);
            updateTitle();
            statusBar.updateFileInfo(tabState);
        });
        tabStates.add(tabState);
        String tabTitle = tabState.getTabTitle();
        tabbedPane.addTab(tabTitle, tabPanel, tabState);
        removeWelcomeTab();

        // Select the new tab
        tabbedPane.setSelectedIndex(tabbedPane.getTabCount() - 1);

        updateTitle();
        statusBar.updateFileInfo(tabState);
    }


    private void createNewTabWithPreprocessedData(File file, List<NDFValue.ObjectValue> ndfObjects,
                                                 NDFValue.NDFFileType fileType, NDFParser parser,
                                                 PropertyScanner propertyScanner, DefaultListModel<NDFValue.ObjectValue> listModel) {
        FileTabState tabState = new FileTabState(file, ndfObjects, fileType, parser);
        FileTabPanel tabPanel = new FileTabPanel(tabState, propertyScanner, listModel);
        tabPanel.addModificationListener(e -> {
            tabState.setModified(true);
            updateTabTitle(tabState);
            updateTitle();
            statusBar.updateFileInfo(tabState);
        });
        tabStates.add(tabState);
        String tabTitle = tabState.getTabTitle();
        tabbedPane.addTab(tabTitle, tabPanel, tabState);
        removeWelcomeTab();

        // Select the new tab
        tabbedPane.setSelectedIndex(tabbedPane.getTabCount() - 1);

        updateTitle();
        statusBar.updateFileInfo(tabState);
    }


    private void showWelcomeTab() {
        if (tabbedPane.getTabCount() == 0) {
            JPanel welcomePanel = new JPanel(new BorderLayout());
            JLabel welcomeLabel = new JLabel(
                "<html><div style='text-align: center; padding: 50px;'>" +
                "<h2>Welcome to WARNO Mod Maker</h2>" +
                "<p>Open an NDF file to get started</p>" +
                "<p>Use Ctrl+O or File -> Open to load a file</p>" +
                "</div></html>",
                SwingConstants.CENTER
            );
            welcomePanel.add(welcomeLabel, BorderLayout.CENTER);
            tabbedPane.addTab("Welcome", welcomePanel);
        }
    }


    private void removeWelcomeTab() {
        if (tabbedPane.getTabCount() > 0) {
            Component firstTab = tabbedPane.getComponentAt(0);
            if (!(firstTab instanceof FileTabPanel)) {
                tabbedPane.removeTabAt(0);
            }
        }
    }


    private void loadFileInBackground(File file) {
        JDialog progressDialog = new JDialog(this, "Loading File", true);

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setString("Loading " + file.getName() + "...");
        progressBar.setStringPainted(true);

        JLabel statusLabel = new JLabel("Initializing...", SwingConstants.CENTER);

        JPanel progressPanel = new JPanel(new BorderLayout(10, 10));
        progressPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        progressPanel.add(statusLabel, BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.CENTER);

        progressDialog.add(progressPanel);
        progressDialog.setSize(450, 120);
        progressDialog.setLocationRelativeTo(this);
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            private List<NDFValue.ObjectValue> ndfObjects;
            private NDFValue.NDFFileType fileType;
            private NDFParser parser;
            private Exception error;

            @Override
            protected Void doInBackground() throws Exception {
                try {
                    publish("Reading file...");
                    fileType = NDFValue.NDFFileType.fromFilename(file.getName());

                    String sourceContent = Files.readString(file.toPath(), StandardCharsets.UTF_8);

                    publish("Parsing NDF content...");
                    try (Reader reader = new StringReader(sourceContent)) {
                        parser = new NDFParser(reader);
                        parser.setFileType(fileType);
                        parser.setOriginalSourceContent(sourceContent);
                        ndfObjects = parser.parse();
                    }

                    publish("Parsed " + ndfObjects.size() + " objects");
                } catch (Exception e) {
                    error = e;
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    statusLabel.setText(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                try {
                    if (error != null) {
                        progressDialog.dispose();

                        String errorMessage = error.getMessage();
                        if (errorMessage == null || errorMessage.trim().isEmpty()) {
                            errorMessage = error.getClass().getSimpleName() + " occurred during file loading";
                        }

                        JOptionPane.showMessageDialog(
                            MainWindow.this,
                            "Error opening file: " + errorMessage,
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                        );
                        error.printStackTrace();
                    } else {
                        // Update progress text to show UI initialization
                        statusLabel.setText("Initializing UI with " + ndfObjects.size() + " objects...");
                        progressBar.setString("Scanning properties...");

                        // Use a normal Java Thread for heavy operations
                        new Thread(() -> {
                            try {
                                // Do heavy work in background thread
                                // 1. Property scanning
                                PropertyScanner propertyScanner = new PropertyScanner(ndfObjects, fileType);
                                propertyScanner.scanProperties();

                                // 2. Create list model
                                DefaultListModel<NDFValue.ObjectValue> listModel = new DefaultListModel<>();
                                for (NDFValue.ObjectValue obj : ndfObjects) {
                                    listModel.addElement(obj);
                                }

                                // Now update UI on EDT with pre-processed data
                                SwingUtilities.invokeLater(() -> {
                                    createNewTabWithPreprocessedData(file, ndfObjects, fileType, parser, propertyScanner, listModel);

                                    // CRITICAL: Register file with cross-system integrity manager
                                    integrityManager.registerFile(file.getName(), fileType, ndfObjects);

                                    progressDialog.dispose();

                                    String objectTypeName = getObjectTypeNameForFile(file.getName(), fileType);
                                    JOptionPane.showMessageDialog(
                                        MainWindow.this,
                                        "Loaded " + ndfObjects.size() + " " + objectTypeName + ".",
                                        "File Loaded",
                                        JOptionPane.INFORMATION_MESSAGE
                                    );
                                });
                            } catch (Exception ex) {
                                SwingUtilities.invokeLater(() -> {
                                    progressDialog.dispose();
                                    JOptionPane.showMessageDialog(
                                        MainWindow.this,
                                        "Error initializing UI: " + ex.getMessage(),
                                        "Error",
                                        JOptionPane.ERROR_MESSAGE
                                    );
                                });
                            }
                        }).start();
                    }
                } catch (Exception ex) {
                    progressDialog.dispose();
                    JOptionPane.showMessageDialog(
                        MainWindow.this,
                        "Error processing file: " + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        };

        // Start worker first, then show dialog (modal dialog blocks execution)
        worker.execute();
        progressDialog.setVisible(true);
    }

    /**
     * Silent version of loadFileInBackground for auto-load functionality
     */
    private void loadFileInBackgroundSilent(File file) {
        try {
            // Determine file type
            NDFValue.NDFFileType fileType = NDFValue.NDFFileType.fromFilename(file.getName());
            List<NDFValue.ObjectValue> ndfObjects;
            NDFParser parser;

            try (Reader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
                parser = new NDFParser(reader);
                parser.setFileType(fileType);
                ndfObjects = parser.parse();
            }

            // Do heavy work in background thread
            PropertyScanner propertyScanner = new PropertyScanner(ndfObjects, fileType);
            propertyScanner.scanProperties();

            DefaultListModel<NDFValue.ObjectValue> listModel = new DefaultListModel<>();
            for (NDFValue.ObjectValue obj : ndfObjects) {
                listModel.addElement(obj);
            }

            // Update UI on EDT with pre-processed data (no dialog)
            SwingUtilities.invokeLater(() -> {
                createNewTabWithPreprocessedData(file, ndfObjects, fileType, parser, propertyScanner, listModel);
                // CRITICAL: Register file with cross-system integrity manager
                integrityManager.registerFile(file.getName(), fileType, ndfObjects);
                // NO SUCCESS DIALOG for silent loading
            });

        } catch (Exception e) {
            // Still show error dialogs for silent loading
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(
                    MainWindow.this,
                    "Error loading file: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                );
            });
            throw new RuntimeException(e);
        }
    }

    private void saveTabToFile(FileTabState tabState, File file) {
        // Create progress dialog
        JDialog progressDialog = new JDialog(this, "Saving File", true);
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setString("Saving " + file.getName() + "...");
        progressBar.setStringPainted(true);

        progressDialog.add(progressBar);
        progressDialog.setSize(300, 100);
        progressDialog.setLocationRelativeTo(this);

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            private Exception error;

            @Override
            protected Void doInBackground() throws Exception {
                try {
                    saveTabToFileInternal(tabState, file);
                } catch (Exception e) {
                    error = e;
                }
                return null;
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                if (error != null) {
                    JOptionPane.showMessageDialog(
                        MainWindow.this,
                        "Error saving file: " + error.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                    );
                    error.printStackTrace();
                } else {
                    refreshCurrentTabWithProgress();
                    JOptionPane.showMessageDialog(
                        MainWindow.this,
                        "File saved successfully.",
                        "File Saved",
                        JOptionPane.INFORMATION_MESSAGE
                    );
                }
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }

    private void saveTabToFileInternal(FileTabState tabState, File file) throws Exception {
        Path originalPath = file.toPath();
        Path dir = originalPath.getParent();
        if (dir == null) dir = Paths.get(".");
        Path tempPath = dir.resolve(file.getName() + ".saving.tmp");
        Path backupPath = dir.resolve(file.getName() + ".bak");

        // 1) Write to temp file first
        if (Files.exists(tempPath)) {
            Files.delete(tempPath);
        }
        try (Writer writer = new BufferedWriter(new FileWriter(tempPath.toFile(), StandardCharsets.UTF_8))) {
            NDFWriter ndfWriter = new NDFWriter(writer, true); // Enable formatting preservation

            if (tabState.getParser() != null) {
                ndfWriter.setOriginalTokens(tabState.getParser().getOriginalTokens());

                String[] sourceLines = tabState.getParser().getSourceLines();
                if (sourceLines != null) {
                    String originalContent = String.join("\n", sourceLines);
                    ndfWriter.setOriginalSourceContent(originalContent);
                }
            }

            ndfWriter.setModificationTracker(tabState.getModificationTracker());
            markModifiedObjects(ndfWriter, tabState);

            ndfWriter.write(tabState.getUnitDescriptors());
        }

        // 2) Create/refresh backup of the original (if it exists)
        boolean hadOriginal = Files.exists(originalPath);
        if (hadOriginal) {
            Files.move(originalPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // 3) Move temp into place atomically if possible
        try {
            Files.move(tempPath, originalPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception atomicEx) {
            // Fallback to regular move within same directory; backup already taken
            Files.move(tempPath, originalPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // 4) Success: clear modified flag and remove backup
        tabState.setModified(false);
        updateTabTitle(tabState);
        updateTitle();

        if (Files.exists(backupPath)) {
            try { Files.delete(backupPath); } catch (Exception ignore) { /* keep .bak if locked */ }
        }
    }


    private void markModifiedObjects(NDFWriter ndfWriter, FileTabState tabState) {
        // Get all modified unit names from the modification tracker
        ModificationTracker tracker = tabState.getModificationTracker();
        int markedCount = 0;

        if (tracker.hasModifications()) {
            // Get unique unit names that have been modified
            Set<String> modifiedUnitNames = new HashSet<>();
            for (ModificationRecord record : tracker.getAllModifications()) {
                modifiedUnitNames.add(record.getUnitName());
            }

            // Find and mark the corresponding ObjectValue instances
            List<NDFValue.ObjectValue> allObjects = tabState.getUnitDescriptors();
            for (NDFValue.ObjectValue obj : allObjects) {
                String instanceName = obj.getInstanceName();
                if (instanceName != null && modifiedUnitNames.contains(instanceName)) {
                    ndfWriter.markObjectAsModified(obj);
                    markedCount++;
                }
            }
        }


        for (ModificationRecord record : tracker.getAllModifications()) {
            PropertyUpdater.ModificationType modType = record.getModificationType();
            if (modType == PropertyUpdater.ModificationType.OBJECT_ADDED ||
                modType == PropertyUpdater.ModificationType.MODULE_ADDED ||
                modType == PropertyUpdater.ModificationType.PROPERTY_ADDED ||
                modType == PropertyUpdater.ModificationType.ARRAY_ELEMENT_ADDED) {

                // Find the object by name and mark it as modified
                String objectName = record.getUnitName();
                for (NDFValue.ObjectValue obj : tabState.getUnitDescriptors()) {
                    if (objectName.equals(obj.getInstanceName())) {
                        ndfWriter.markObjectAsModified(obj);
                        markedCount++;
                        break;
                    }
                }
            }
        }

        System.out.println("Marked " + markedCount + " total modified objects for writing");
    }


    private void updateTabTitle(FileTabState tabState) {
        int tabIndex = tabStates.indexOf(tabState);
        if (tabIndex >= 0 && tabIndex < tabbedPane.getTabCount()) {
            Component component = tabbedPane.getComponentAt(tabIndex);
            tabbedPane.updateTabState(component, tabState);
        }
    }


    void refreshCurrentTab() {
        FileTabPanel currentPanel = getCurrentTabPanel();
        if (currentPanel != null) {
            currentPanel.refresh();
        }
    }

    private void refreshCurrentTabWithProgress() {
        FileTabPanel currentPanel = getCurrentTabPanel();
        if (currentPanel == null) {
            return;
        }

        // Create progress dialog
        JDialog progressDialog = new JDialog(this, "Updating View", true);

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setString("Refreshing tree view...");
        progressBar.setStringPainted(true);

        JLabel statusLabel = new JLabel("Initializing...", SwingConstants.CENTER);

        JPanel progressPanel = new JPanel(new BorderLayout(10, 10));
        progressPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        progressPanel.add(statusLabel, BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.CENTER);

        progressDialog.add(progressPanel);
        progressDialog.setSize(450, 120);
        progressDialog.setLocationRelativeTo(this);

        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                // Get the current tab state
                FileTabState tabState = currentPanel.getTabState();

                publish("Scanning properties...");

                // Do the heavy work in background: property scanning
                PropertyScanner propertyScanner = null;
                DefaultListModel<NDFValue.ObjectValue> listModel = null;

                if (tabState.hasData()) {
                    // Create property scanner in background
                    propertyScanner = new PropertyScanner(tabState.getUnitDescriptors(), tabState.getFileType());
                    propertyScanner.scanProperties();

                    publish("Building list model...");

                    // Create list model in background
                    listModel = new DefaultListModel<>();
                    for (NDFValue.ObjectValue unit : tabState.getUnitDescriptors()) {
                        listModel.addElement(unit);
                    }
                }

                publish("Updating UI...");

                // Update UI on EDT with pre-processed data
                final PropertyScanner finalScanner = propertyScanner;
                final DefaultListModel<NDFValue.ObjectValue> finalModel = listModel;

                SwingUtilities.invokeLater(() -> {
                    if (tabState.hasData() && finalScanner != null && finalModel != null) {
                        // Use the optimized method with pre-processed data
                        currentPanel.updateFromTabStateWithPreprocessedData(finalScanner, finalModel);
                    } else {
                        // Fallback for empty tabs
                        currentPanel.updateFromTabState();
                    }
                });

                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    progressBar.setString(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                progressDialog.dispose();
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }

    /**
     * Highlight a newly created entity in the current tab
     */
    private void highlightNewEntity(String entityName) {
        FileTabPanel currentPanel = getCurrentTabPanel();

        if (currentPanel != null && entityName != null) {
            // Use SwingUtilities.invokeLater to ensure the refresh is complete before highlighting
            SwingUtilities.invokeLater(() -> {
                currentPanel.selectAndHighlightEntity(entityName);
            });
        }
    }

    /**
     * Refresh any open modification dialogs to show newly added objects
     */
    private void refreshModificationDialogs() {
        // Note: In a more complex implementation, we would track open dialogs
        // and refresh their object lists. For now, users will need to close
        // and reopen modification dialogs to see new objects.
        // This could be enhanced by implementing a dialog registry system.
    }


    private void closeCurrentTab(ActionEvent e) {
        int selectedIndex = tabbedPane.getSelectedIndex();
        if (selectedIndex >= 0) {
            closeTab(selectedIndex);
        }
    }


    private void closeTab(int tabIndex) {
        if (tabIndex < 0 || tabIndex >= tabStates.size()) {
            return;
        }

        FileTabState tabState = tabStates.get(tabIndex);
        if (tabState.isModified()) {
            String fileName = tabState.getFile() != null ? tabState.getFile().getName() : "Untitled";
            int result = JOptionPane.showConfirmDialog(
                this,
                "The file '" + fileName + "' has been modified. Save changes?",
                "Save Changes",
                JOptionPane.YES_NO_CANCEL_OPTION
            );

            if (result == JOptionPane.YES_OPTION) {
                if (tabState.getFile() != null) {
                    saveTabToFile(tabState, tabState.getFile());
                } else {
                    // Would need to implement save as for untitled tabs
                    return;
                }
            } else if (result == JOptionPane.CANCEL_OPTION) {
                return;
            }
        }


        if (tabState.getFile() != null) {
            integrityManager.unregisterFile(tabState.getFile().getName());
        }

        tabStates.remove(tabIndex);
        tabbedPane.removeTabAt(tabIndex);
        if (tabStates.isEmpty()) {
            showWelcomeTab();
        }

        updateTitle();
    }





    private void reloadTabFromDisk(int tabIndex, File file) {
        if (tabIndex < 0 || tabIndex >= tabStates.size()) {
            return;
        }

        // Switch to the tab first
        tabbedPane.setSelectedIndex(tabIndex);

        // Load the file in background and replace the tab content
        JDialog progressDialog = new JDialog(this, "Reloading File", true);
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setString("Reloading " + file.getName() + "...");
        progressBar.setStringPainted(true);

        progressDialog.add(progressBar);
        progressDialog.setSize(400, 100);
        progressDialog.setLocationRelativeTo(this);

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            private List<NDFValue.ObjectValue> ndfObjects;
            private NDFValue.NDFFileType fileType;
            private NDFParser parser;
            private Exception error;

            @Override
            protected Void doInBackground() throws Exception {
                try {
                    // Small delay to ensure dialog is visible
                    Thread.sleep(100);

                    // Determine file type
                    fileType = NDFValue.NDFFileType.fromFilename(file.getName());
                    try (Reader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
                        parser = new NDFParser(reader);
                        parser.setFileType(fileType);
                        ndfObjects = parser.parse();
                    }
                } catch (Exception e) {
                    error = e;
                }
                return null;
            }

            @Override
            protected void done() {
                progressDialog.dispose();

                if (error != null) {
                    String errorMessage = error.getMessage();
                    if (errorMessage == null || errorMessage.trim().isEmpty()) {
                        errorMessage = error.getClass().getSimpleName() + " occurred during file loading";
                    }

                    JOptionPane.showMessageDialog(
                        MainWindow.this,
                        "Error reloading file: " + errorMessage,
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                    );
                    error.printStackTrace();
                } else {
                    // Replace the existing tab content with fresh data
                    FileTabState tabState = tabStates.get(tabIndex);
                    tabState.setUnitDescriptors(ndfObjects);
                    tabState.setFileType(fileType);
                    tabState.setParser(parser);
                    tabState.setModified(false);
                    tabState.getModificationTracker().clearModifications();

                    // Refresh the tab panel with the fresh data
                    FileTabPanel tabPanel = (FileTabPanel) tabbedPane.getComponentAt(tabIndex);
                    tabPanel.updateFromTabState();

                    updateTabTitle(tabState);
                    updateTitle();
                    statusBar.updateFileInfo(tabState);

                    String objectTypeName = getObjectTypeNameForFile(file.getName(), fileType);
                    JOptionPane.showMessageDialog(
                        MainWindow.this,
                        "Reloaded " + ndfObjects.size() + " " + objectTypeName + " from disk.",
                        "File Reloaded",
                        JOptionPane.INFORMATION_MESSAGE
                    );
                }
            }
        };

        // Start worker first, then show dialog (modal dialog blocks execution)
        worker.execute();
        progressDialog.setVisible(true);
    }


    private void closeAllTabs(ActionEvent e) {
        List<FileTabState> modifiedTabs = new ArrayList<>();
        for (FileTabState tabState : tabStates) {
            if (tabState.isModified()) {
                modifiedTabs.add(tabState);
            }
        }

        if (!modifiedTabs.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append("The following files have been modified:\n\n");
            for (FileTabState tabState : modifiedTabs) {
                String fileName = tabState.getFile() != null ? tabState.getFile().getName() : "Untitled";
                message.append("- ").append(fileName).append("\n");
            }
            message.append("\nSave changes before closing?");

            int result = JOptionPane.showConfirmDialog(
                this,
                message.toString(),
                "Save Changes",
                JOptionPane.YES_NO_CANCEL_OPTION
            );

            if (result == JOptionPane.YES_OPTION) {
                for (FileTabState tabState : modifiedTabs) {
                    if (tabState.getFile() != null) {
                        saveTabToFile(tabState, tabState.getFile());
                    }
                }
            } else if (result == JOptionPane.CANCEL_OPTION) {
                return;
            }
        }


        for (FileTabState tabState : tabStates) {
            if (tabState.getFile() != null) {
                integrityManager.unregisterFile(tabState.getFile().getName());
            }
        }

        // Close all tabs
        tabStates.clear();
        tabbedPane.removeAll();
        showWelcomeTab();
        updateTitle();
    }


    private void onTabChanged(ChangeEvent e) {
        updateTitle();
        statusBar.updateFileInfo(getCurrentTabState());
    }


    private void setupTabKeyBindings() {
        // Ctrl+Tab - Next tab
        tabbedPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK), "nextTab");
        tabbedPane.getActionMap().put("nextTab", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int selectedIndex = tabbedPane.getSelectedIndex();
                int nextIndex = (selectedIndex + 1) % tabbedPane.getTabCount();
                tabbedPane.setSelectedIndex(nextIndex);
            }
        });

        // Ctrl+Shift+Tab - Previous tab
        tabbedPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "prevTab");
        tabbedPane.getActionMap().put("prevTab", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int selectedIndex = tabbedPane.getSelectedIndex();
                int prevIndex = selectedIndex - 1;
                if (prevIndex < 0) prevIndex = tabbedPane.getTabCount() - 1;
                tabbedPane.setSelectedIndex(prevIndex);
            }
        });
    }


    private void showTabContextMenu(MouseEvent e, int tabIndex) {
        if (tabIndex < 0 || tabIndex >= tabStates.size()) {
            return;
        }

        FileTabState tabState = tabStates.get(tabIndex);
        JPopupMenu contextMenu = new JPopupMenu();

        // Close this tab
        JMenuItem closeItem = new JMenuItem("Close");
        closeItem.addActionListener(evt -> closeTab(tabIndex));
        contextMenu.add(closeItem);

        // Close other tabs
        if (tabStates.size() > 1) {
            JMenuItem closeOthersItem = new JMenuItem("Close Others");
            closeOthersItem.addActionListener(evt -> closeOtherTabs(tabIndex));
            contextMenu.add(closeOthersItem);

            JMenuItem closeAllItem = new JMenuItem("Close All");
            closeAllItem.addActionListener(evt -> closeAllTabs(null));
            contextMenu.add(closeAllItem);
        }

        contextMenu.addSeparator();
        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.setEnabled(tabState.getFile() != null);
        saveItem.addActionListener(evt -> {
            tabbedPane.setSelectedIndex(tabIndex);
            saveFile(null);
        });
        contextMenu.add(saveItem);

        JMenuItem saveAsItem = new JMenuItem("Save As...");
        saveAsItem.setEnabled(tabState.hasData());
        saveAsItem.addActionListener(evt -> {
            tabbedPane.setSelectedIndex(tabIndex);
            saveFileAs(null);
        });
        contextMenu.add(saveAsItem);

        contextMenu.addSeparator();

        // File info
        String fileName = tabState.getFile() != null ? tabState.getFile().getName() : "Untitled";
        JMenuItem infoItem = new JMenuItem("File: " + fileName);
        infoItem.setEnabled(false);
        contextMenu.add(infoItem);

        if (tabState.hasData()) {
            JMenuItem unitsItem = new JMenuItem("Units: " + tabState.getUnitDescriptors().size());
            unitsItem.setEnabled(false);
            contextMenu.add(unitsItem);

            if (tabState.getModificationTracker().hasModifications()) {
                JMenuItem modsItem = new JMenuItem("Modifications: " + tabState.getModificationTracker().getModificationCount());
                modsItem.setEnabled(false);
                contextMenu.add(modsItem);
            }
        }

        contextMenu.show(tabbedPane, e.getX(), e.getY());
    }


    private void closeOtherTabs(int keepTabIndex) {
        if (keepTabIndex < 0 || keepTabIndex >= tabStates.size()) {
            return;
        }
        List<FileTabState> modifiedTabs = new ArrayList<>();
        for (int i = 0; i < tabStates.size(); i++) {
            if (i != keepTabIndex && tabStates.get(i).isModified()) {
                modifiedTabs.add(tabStates.get(i));
            }
        }

        if (!modifiedTabs.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append("The following files have been modified:\n\n");
            for (FileTabState tabState : modifiedTabs) {
                String fileName = tabState.getFile() != null ? tabState.getFile().getName() : "Untitled";
                message.append("- ").append(fileName).append("\n");
            }
            message.append("\nSave changes before closing?");

            int result = JOptionPane.showConfirmDialog(
                this,
                message.toString(),
                "Save Changes",
                JOptionPane.YES_NO_CANCEL_OPTION
            );

            if (result == JOptionPane.YES_OPTION) {
                for (FileTabState tabState : modifiedTabs) {
                    if (tabState.getFile() != null) {
                        saveTabToFile(tabState, tabState.getFile());
                    }
                }
            } else if (result == JOptionPane.CANCEL_OPTION) {
                return;
            }
        }

        // Close tabs in reverse order to maintain indices
        for (int i = tabStates.size() - 1; i >= 0; i--) {
            if (i != keepTabIndex) {

                FileTabState tabState = tabStates.get(i);
                if (tabState.getFile() != null) {
                    integrityManager.unregisterFile(tabState.getFile().getName());
                }

                tabStates.remove(i);
                tabbedPane.removeTabAt(i);
                // Adjust keepTabIndex if necessary
                if (i < keepTabIndex) {
                    keepTabIndex--;
                }
            }
        }

        // Select the remaining tab
        if (keepTabIndex >= 0 && keepTabIndex < tabbedPane.getTabCount()) {
            tabbedPane.setSelectedIndex(keepTabIndex);
        }

        updateTitle();
    }




    private String getObjectTypeNameForFile(String fileName, NDFValue.NDFFileType fileType) {
        if (fileType != NDFValue.NDFFileType.UNKNOWN) {
            return fileType.getDisplayName().toLowerCase() + " descriptors";
        }

        // For unknown file types, use the file name without extension
        String baseName = fileName;
        if (baseName.toLowerCase().endsWith(".ndf")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }
        return baseName + " objects";
    }
}
