package com.warnomodmaker.gui.components;

import com.warnomodmaker.gui.theme.WarnoTheme;
import com.warnomodmaker.model.FileTabState;
import com.warnomodmaker.model.NDFValue.NDFFileType;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Enhanced tabbed pane with file type icons, modification indicators, and close buttons
 */
public class EnhancedTabbedPane extends JTabbedPane {

    private Map<Component, FileTabState> tabStateMap = new HashMap<>();

    public EnhancedTabbedPane() {
        super();
        setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        setupCustomUI();
    }

    private void setupCustomUI() {
        // Add mouse listener for close button functionality
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isMiddleMouseButton(e)) {
                    int tabIndex = indexAtLocation(e.getX(), e.getY());
                    if (tabIndex >= 0) {
                        fireTabCloseEvent(tabIndex);
                    }
                }
            }
        });
    }

    public void addTab(String title, Component component, FileTabState tabState) {
        super.addTab(title, component);
        tabStateMap.put(component, tabState);

        int index = indexOfComponent(component);
        setTabComponentAt(index, createTabComponent(title, tabState, index));

        // Set tooltip with full file path
        if (tabState != null && tabState.getFile() != null) {
            setToolTipTextAt(index, tabState.getFile().getAbsolutePath());
        }
    }

    @Override
    public void removeTabAt(int index) {
        Component component = getComponentAt(index);
        tabStateMap.remove(component);
        super.removeTabAt(index);
    }

    @Override
    public void removeAll() {
        tabStateMap.clear();
        super.removeAll();
    }

    private JPanel createTabComponent(String title, FileTabState tabState, int index) {
        JPanel tabPanel = new JPanel(new BorderLayout());
        tabPanel.setOpaque(false);

        // Left side: Icon and title
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        leftPanel.setOpaque(false);

        // File type icon
        JLabel iconLabel = new JLabel(getFileTypeIcon(tabState));
        iconLabel.setFont(iconLabel.getFont().deriveFont(12f));
        leftPanel.add(iconLabel);

        // Title with modification indicator
        JLabel titleLabel = new JLabel();
        updateTabTitle(titleLabel, title, tabState);
        leftPanel.add(titleLabel);

        tabPanel.add(leftPanel, BorderLayout.CENTER);

        // Right side: Close button
        JButton closeButton = createCloseButton(index);
        tabPanel.add(closeButton, BorderLayout.EAST);

        return tabPanel;
    }

    private void updateTabTitle(JLabel titleLabel, String title, FileTabState tabState) {
        StringBuilder html = new StringBuilder("<html>");

        // Add modification indicator
        if (tabState != null && tabState.isModified()) {
            html.append("<font color='").append(colorToHex(WarnoTheme.WARNING_ORANGE)).append("'>*</font> ");
        }

        // Add title
        html.append(escapeHtml(title));
        html.append("</html>");

        titleLabel.setText(html.toString());
        titleLabel.setForeground(Color.WHITE);
    }

    private String getFileTypeIcon(FileTabState tabState) {
        if (tabState == null) return "[F]";

        NDFFileType fileType = tabState.getFileType();
        switch (fileType) {
            case UNITE_DESCRIPTOR:
                return "[U]"; // Unit icon
            case WEAPON_DESCRIPTOR:
                return "[W]"; // Weapon icon
            case AMMUNITION:
                return "[A]"; // Ammunition icon
            case EXPERIENCE_LEVELS:
                return "[X]"; // Experience icon
            case NDF_DEPICTION_LIST:
                return "[D]"; // Depiction icon
            case ORDER_AVAILABILITY_TACTIC:
                return "[O]"; // Orders icon
            default:
                return "[F]"; // Generic file icon
        }
    }

    private JButton createCloseButton(int initialIndex) {
        JButton closeButton = new JButton("X");
        closeButton.setPreferredSize(new Dimension(16, 16));
        closeButton.setFont(closeButton.getFont().deriveFont(Font.BOLD, 12f));
        closeButton.setForeground(Color.LIGHT_GRAY);
        closeButton.setBackground(new Color(0, 0, 0, 0));
        closeButton.setBorder(null);
        closeButton.setFocusPainted(false);
        closeButton.setContentAreaFilled(false);
        closeButton.setOpaque(false);

        // Add ActionListener as primary close mechanism (more reliable than mouse events)
        closeButton.addActionListener(e -> {
            int currentIndex = findTabIndexForCloseButton(closeButton);
            if (currentIndex >= 0) {
                fireTabCloseEvent(currentIndex);
            }
        });

        // Hover effects
        closeButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                closeButton.setForeground(WarnoTheme.ERROR_RED);
                closeButton.setContentAreaFilled(true);
                closeButton.setBackground(WarnoTheme.TACTICAL_GREEN_LIGHT);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                closeButton.setForeground(Color.LIGHT_GRAY);
                closeButton.setContentAreaFilled(false);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                // Backup mechanism - only handle left mouse button clicks
                if (SwingUtilities.isLeftMouseButton(e)) {
                    int currentIndex = findTabIndexForCloseButton(closeButton);
                    if (currentIndex >= 0) {
                        fireTabCloseEvent(currentIndex);
                    }
                }
            }
        });

        return closeButton;
    }

    /**
     * Find the current tab index for a close button by traversing up the component hierarchy
     */
    private int findTabIndexForCloseButton(JButton closeButton) {
        // Walk up the component hierarchy to find the tab component
        Container parent = closeButton.getParent();
        while (parent != null && !(parent instanceof JPanel)) {
            parent = parent.getParent();
        }

        if (parent != null) {
            // Search through all tabs to find which one contains this tab component
            for (int i = 0; i < getTabCount(); i++) {
                Component tabComponent = getTabComponentAt(i);
                if (tabComponent == parent) {
                    return i;
                }
            }
        }

        return -1; // Not found
    }

    private void fireTabCloseEvent(int index) {
        // Validate index before firing event
        if (index >= 0 && index < getTabCount()) {
            // Fire a property change event that MainWindow can listen to
            firePropertyChange("tabClose", -1, index);
        }
    }

    public void updateTabState(Component component, FileTabState tabState) {
        tabStateMap.put(component, tabState);

        int index = indexOfComponent(component);
        if (index >= 0) {
            // Get the base title from the tab state, not from the current tab title
            String title = (tabState != null && tabState.getFile() != null)
                ? tabState.getFile().getName()
                : "Untitled";

            setTabComponentAt(index, createTabComponent(title, tabState, index));

            // Update tooltip
            if (tabState != null && tabState.getFile() != null) {
                setToolTipTextAt(index, tabState.getFile().getAbsolutePath());
            }
        }
    }

    public FileTabState getTabState(Component component) {
        return tabStateMap.get(component);
    }

    public FileTabState getTabState(int index) {
        if (index >= 0 && index < getTabCount()) {
            Component component = getComponentAt(index);
            return tabStateMap.get(component);
        }
        return null;
    }

    private String colorToHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;");
    }
}
