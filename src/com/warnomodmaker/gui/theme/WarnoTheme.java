package com.warnomodmaker.gui.theme;

import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.UIManager;
import java.awt.Color;

/**
 * Custom FlatLaf theme for WARNO Mod Maker with military-inspired styling
 */
public class WarnoTheme extends FlatDarkLaf {
    
    public static final String NAME = "WARNO Dark Theme";
    
    // Military-inspired color palette
    public static final Color TACTICAL_GREEN_DARK = new Color(45, 58, 45);    // #2d3a2d
    public static final Color TACTICAL_GREEN_LIGHT = new Color(58, 74, 58);   // #3a4a3a
    public static final Color ACCENT_BLUE = new Color(74, 111, 165);          // #4a6fa5
    public static final Color ACCENT_BLUE_LIGHT = new Color(90, 127, 181);    // #5a7fb5
    public static final Color WARNING_ORANGE = new Color(212, 133, 31);       // #d4851f
    public static final Color ACTION_ORANGE = new Color(230, 149, 31);        // #e6951f
    public static final Color SUCCESS_GREEN = new Color(76, 175, 80);         // #4caf50
    public static final Color ERROR_RED = new Color(244, 67, 54);             // #f44336
    
    public static boolean setup() {
        try {
            UIManager.setLookAndFeel(new WarnoTheme());
            
            // Apply custom colors
            UIManager.put("Component.focusColor", ACCENT_BLUE);
            UIManager.put("Component.borderColor", TACTICAL_GREEN_LIGHT);
            UIManager.put("Component.disabledBorderColor", TACTICAL_GREEN_DARK);
            
            // Button styling
            UIManager.put("Button.background", TACTICAL_GREEN_LIGHT);
            UIManager.put("Button.focusedBackground", ACCENT_BLUE);
            UIManager.put("Button.hoverBackground", ACCENT_BLUE_LIGHT);
            UIManager.put("Button.pressedBackground", ACCENT_BLUE);
            UIManager.put("Button.selectedBackground", ACCENT_BLUE);
            
            // Tab styling
            UIManager.put("TabbedPane.selectedBackground", TACTICAL_GREEN_LIGHT);
            UIManager.put("TabbedPane.hoverColor", ACCENT_BLUE_LIGHT);
            UIManager.put("TabbedPane.focusColor", ACCENT_BLUE);
            UIManager.put("TabbedPane.underlineColor", ACCENT_BLUE);
            UIManager.put("TabbedPane.selectedForeground", Color.WHITE);
            
            // Tree styling
            UIManager.put("Tree.selectionBackground", ACCENT_BLUE);
            UIManager.put("Tree.selectionBorderColor", ACCENT_BLUE_LIGHT);
            UIManager.put("Tree.background", TACTICAL_GREEN_DARK);
            
            // List styling
            UIManager.put("List.selectionBackground", ACCENT_BLUE);
            UIManager.put("List.selectionForeground", Color.WHITE);
            UIManager.put("List.background", TACTICAL_GREEN_DARK);
            
            // Panel and border styling
            UIManager.put("Panel.background", TACTICAL_GREEN_DARK);
            UIManager.put("TitledBorder.titleColor", ACCENT_BLUE_LIGHT);
            
            // Menu styling
            UIManager.put("MenuBar.background", TACTICAL_GREEN_LIGHT);
            UIManager.put("Menu.background", TACTICAL_GREEN_LIGHT);
            UIManager.put("MenuItem.background", TACTICAL_GREEN_LIGHT);
            UIManager.put("MenuItem.selectionBackground", ACCENT_BLUE);
            
            // Text field styling
            UIManager.put("TextField.background", TACTICAL_GREEN_DARK);
            UIManager.put("TextField.focusedBackground", TACTICAL_GREEN_LIGHT);
            UIManager.put("TextField.selectionBackground", ACCENT_BLUE);
            
            // Scroll pane styling
            UIManager.put("ScrollPane.background", TACTICAL_GREEN_DARK);
            UIManager.put("ScrollBar.track", TACTICAL_GREEN_DARK);
            UIManager.put("ScrollBar.thumb", TACTICAL_GREEN_LIGHT);
            UIManager.put("ScrollBar.hoverThumbColor", ACCENT_BLUE_LIGHT);
            UIManager.put("ScrollBar.pressedThumbColor", ACCENT_BLUE);
            
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    @Override
    public String getName() {
        return NAME;
    }
    
    @Override
    public String getDescription() {
        return "Military-inspired dark theme for WARNO Mod Maker";
    }
    
    @Override
    public boolean isDark() {
        return true;
    }
}
