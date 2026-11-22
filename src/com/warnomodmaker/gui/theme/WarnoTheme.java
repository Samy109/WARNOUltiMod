package com.warnomodmaker.gui.theme;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.FontUIResource;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The application's look: slate surfaces with one field-green accent, rounded controls and Segoe Fluent icons.
 * Text meets 4.5:1 contrast on every surface it sits on. Every colour in the UI comes from these tokens.
 */
public final class WarnoTheme {
    // Surfaces, darkest to lightest.
    public static final Color BACKGROUND = hex("#0E1014");
    public static final Color SIDEBAR = hex("#13161B");
    public static final Color PANEL = hex("#181C22");
    public static final Color RAISED = hex("#1F242C");
    public static final Color INPUT = hex("#0F1216");
    public static final Color HOVER = hex("#252B35");
    public static final Color ALT_ROW = hex("#14171C");
    public static final Color LOG = hex("#0B0D10");
    public static final Color BORDER = hex("#2A303A");
    public static final Color STRONG_BORDER = hex("#3A4250");
    // Text.
    public static final Color TEXT = hex("#E8EAEF");
    public static final Color MUTED = hex("#9AA3B2");
    public static final Color FAINT = hex("#6B7484");
    public static final Color LOG_TEXT = hex("#C5CBD6");
    // The accent.
    public static final Color ACCENT = hex("#7FB55E");
    public static final Color ACCENT_HOVER = hex("#93C673");
    public static final Color ACCENT_PRESSED = hex("#6A9C4B");
    public static final Color ACCENT_TEXT = hex("#0B1407");
    public static final Color ACCENT_SOFT = hex("#1F2C19");
    public static final Color ACCENT_SOFT_BORDER = hex("#35502A");
    public static final Color SELECTION = hex("#2A3A22");
    // Status colours, each readable on every surface.
    public static final Color SUCCESS = hex("#9BD27A");
    public static final Color WARNING = hex("#F0A86E");
    public static final Color CAUTION = hex("#E9C46A");
    public static final Color INFO = hex("#7CC9BE");
    public static final Color DANGER = hex("#E5787A");

    /** A decision badge: background, border, dot and text colours. */
    public static final class Badge {
        public final Color background, border, dot, text;

        Badge(String background, String border, String dot, String text) {
            this.background = hex(background);
            this.border = hex(border);
            this.dot = hex(dot);
            this.text = hex(text);
        }
    }

    private static final Map<String, Badge> BADGES = new HashMap<>();

    static {
        BADGES.put("Merged", new Badge("#18261A", "#2F4D2B", "#9BD27A", "#BFE6A6"));
        BADGES.put("ValueConflict", new Badge("#2A1D14", "#5A3A22", "#F0A86E", "#F6C7A0"));
        BADGES.put("Override", new Badge("#272114", "#54451F", "#E9C46A", "#F1DA9C"));
        BADGES.put("Single", new Badge("#14242A", "#244650", "#7CC9BE", "#AEE0D8"));
        BADGES.put("Delete", new Badge("#2B1718", "#5C2C2E", "#E5787A", "#F2B3B4"));
        BADGES.put("Identical", new Badge("#1C2026", "#323844", "#9AA3B2", "#C9CED8"));
    }

    public static Badge badge(String key) {
        Badge badge = BADGES.get(key);
        return badge != null ? badge : BADGES.get("Identical");
    }

    // Fonts. Segoe UI ships with Windows; the fallbacks keep the app usable elsewhere.
    private static final String UI_FAMILY = firstInstalled("Segoe UI", Font.SANS_SERIF);
    private static final String SEMIBOLD_FAMILY = firstInstalled("Segoe UI Semibold", null);
    private static final String MONO_FAMILY = firstInstalled("Cascadia Mono", "Consolas", Font.MONOSPACED);
    private static final String ICON_FAMILY = firstInstalled("Segoe Fluent Icons", "Segoe MDL2 Assets", null);

    private WarnoTheme() {
    }

    public static String html(Color color) {
        return String.format("#%06x", color.getRGB() & 0xffffff);
    }

    public static Color hex(String hex) {
        return Color.decode(hex);
    }

    private static String firstInstalled(String... families) {
        List<String> installed = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String family : families) {
            if (family == null) return null;
            if (installed.contains(family) || family.equals(Font.SANS_SERIF) || family.equals(Font.MONOSPACED)) return family;
        }
        return null;
    }

    public static Font ui(float size) {
        return new Font(UI_FAMILY, Font.PLAIN, Math.round(size)).deriveFont(size);
    }

    /** Semibold where Segoe UI Semibold is installed, bold otherwise. */
    public static Font semibold(float size) {
        return SEMIBOLD_FAMILY != null ? new Font(SEMIBOLD_FAMILY, Font.PLAIN, Math.round(size)).deriveFont(size)
            : new Font(UI_FAMILY, Font.BOLD, Math.round(size)).deriveFont(size);
    }

    public static Font mono(float size) {
        return new Font(MONO_FAMILY, Font.PLAIN, Math.round(size)).deriveFont(size);
    }

    /** True when a Fluent/MDL2 icon font is installed (Windows 10 and later). */
    public static boolean hasIcons() {
        return ICON_FAMILY != null;
    }

    public static Font icons(float size) {
        return new Font(ICON_FAMILY != null ? ICON_FAMILY : UI_FAMILY, Font.PLAIN, Math.round(size)).deriveFont(size);
    }

    /** Installs the theme. Returns false when FlatLaf can't be set up. */
    public static boolean setup() {
        try {
            Map<String, String> variables = new HashMap<>();
            variables.put("@background", "#0E1014");
            variables.put("@foreground", "#E8EAEF");
            variables.put("@accentColor", "#7FB55E");
            variables.put("@selectionBackground", "#2A3A22");
            variables.put("@selectionForeground", "#E8EAEF");
            variables.put("@selectionInactiveBackground", "#22301C");
            variables.put("@disabledForeground", "#6B7484");
            FlatLaf.setGlobalExtraDefaults(variables);
            FlatDarkLaf.setup();
            apply();
            return true;
        } catch (RuntimeException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void apply() {
        UIManager.put("defaultFont", new FontUIResource(ui(13)));
        // Shape
        UIManager.put("Component.arc", 8);
        UIManager.put("Button.arc", 8);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("CheckBox.arc", 4);
        UIManager.put("ProgressBar.arc", 6);
        UIManager.put("Component.focusWidth", 0);
        UIManager.put("Component.innerFocusWidth", 1);
        UIManager.put("Button.innerFocusWidth", 0);
        UIManager.put("Component.focusColor", ACCENT);
        UIManager.put("Component.focusedBorderColor", ACCENT);
        UIManager.put("Component.borderColor", STRONG_BORDER);
        UIManager.put("Component.disabledBorderColor", BORDER);
        UIManager.put("Component.hoverBorderColor", FAINT);
        UIManager.put("Component.accentColor", ACCENT);
        // Surfaces
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("RootPane.background", BACKGROUND);
        UIManager.put("Viewport.background", PANEL);
        UIManager.put("ScrollPane.background", PANEL);
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("SplitPane.background", BACKGROUND);
        UIManager.put("SplitPaneDivider.draggingColor", ACCENT);
        UIManager.put("SplitPane.dividerSize", 6);
        UIManager.put("Separator.foreground", BORDER);
        UIManager.put("TitledBorder.titleColor", MUTED);
        UIManager.put("TitledBorder.border", BorderFactory.createLineBorder(BORDER));
        // Title bar and menus
        UIManager.put("TitlePane.background", SIDEBAR);
        UIManager.put("TitlePane.inactiveBackground", SIDEBAR);
        UIManager.put("TitlePane.foreground", TEXT);
        UIManager.put("TitlePane.inactiveForeground", MUTED);
        UIManager.put("TitlePane.unifiedBackground", true);
        UIManager.put("TitlePane.menuBarEmbedded", true);
        UIManager.put("MenuBar.background", SIDEBAR);
        UIManager.put("MenuBar.borderColor", BORDER);
        UIManager.put("MenuBar.hoverBackground", HOVER);
        UIManager.put("MenuBar.underlineSelectionColor", ACCENT);
        UIManager.put("PopupMenu.background", RAISED);
        UIManager.put("PopupMenu.borderColor", STRONG_BORDER);
        UIManager.put("Menu.background", RAISED);
        UIManager.put("MenuItem.background", RAISED);
        UIManager.put("MenuItem.selectionBackground", HOVER);
        UIManager.put("Menu.selectionBackground", HOVER);
        UIManager.put("MenuItem.acceleratorForeground", FAINT);
        UIManager.put("MenuItem.selectionType", "underline");
        UIManager.put("MenuItem.underlineSelectionColor", ACCENT);
        UIManager.put("PopupMenu.borderInsets", new Insets(4, 0, 4, 0));
        // Buttons: secondary by default; accent buttons set JButton.buttonType=default (see accentButton).
        UIManager.put("Button.background", RAISED);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("Button.borderColor", STRONG_BORDER);
        UIManager.put("Button.hoverBackground", HOVER);
        UIManager.put("Button.hoverBorderColor", STRONG_BORDER);
        UIManager.put("Button.pressedBackground", BORDER);
        UIManager.put("Button.focusedBorderColor", ACCENT);
        UIManager.put("Button.selectedBackground", SELECTION);
        UIManager.put("Button.disabledText", FAINT);
        UIManager.put("Button.font", semibold(13));
        UIManager.put("Button.margin", new Insets(7, 14, 7, 14));
        UIManager.put("Button.default.background", ACCENT);
        UIManager.put("Button.default.foreground", ACCENT_TEXT);
        UIManager.put("Button.default.borderColor", ACCENT);
        UIManager.put("Button.default.hoverBackground", ACCENT_HOVER);
        UIManager.put("Button.default.hoverBorderColor", ACCENT_HOVER);
        UIManager.put("Button.default.pressedBackground", ACCENT_PRESSED);
        UIManager.put("Button.default.focusedBorderColor", ACCENT_HOVER);
        UIManager.put("Button.default.focusColor", ACCENT_HOVER);
        UIManager.put("Button.default.boldText", false);
        UIManager.put("Button.toolbar.hoverBackground", HOVER);
        UIManager.put("Button.toolbar.pressedBackground", BORDER);
        UIManager.put("ToggleButton.background", RAISED);
        UIManager.put("ToggleButton.selectedBackground", SELECTION);
        UIManager.put("ToggleButton.selectedForeground", TEXT);
        // Inputs
        for (String key : new String[]{"TextField", "FormattedTextField", "PasswordField", "TextArea", "TextPane", "EditorPane", "Spinner", "ComboBox"}) {
            UIManager.put(key + ".background", INPUT);
            UIManager.put(key + ".foreground", TEXT);
        }
        UIManager.put("TextField.placeholderForeground", FAINT);
        UIManager.put("TextComponent.selectionBackground", ACCENT_PRESSED);
        UIManager.put("TextComponent.selectionForeground", TEXT);
        UIManager.put("TextField.margin", new Insets(6, 10, 6, 10));
        UIManager.put("ComboBox.buttonBackground", INPUT);
        UIManager.put("ComboBox.buttonEditableBackground", INPUT);
        UIManager.put("ComboBox.buttonArrowColor", MUTED);
        UIManager.put("ComboBox.popupBackground", RAISED);
        UIManager.put("ComboBox.selectionBackground", SELECTION);
        UIManager.put("ComboBox.padding", new Insets(6, 10, 6, 6));
        UIManager.put("CheckBox.icon.selectedBackground", ACCENT);
        UIManager.put("CheckBox.icon.selectedBorderColor", ACCENT);
        UIManager.put("CheckBox.icon.checkmarkColor", ACCENT_TEXT);
        UIManager.put("CheckBox.icon.background", INPUT);
        UIManager.put("CheckBox.icon.borderColor", STRONG_BORDER);
        // Lists, trees and tables
        UIManager.put("List.background", PANEL);
        UIManager.put("List.selectionBackground", SELECTION);
        UIManager.put("List.selectionForeground", TEXT);
        UIManager.put("List.selectionInactiveBackground", hex("#22301C"));
        UIManager.put("Tree.background", PANEL);
        UIManager.put("Tree.selectionBackground", SELECTION);
        UIManager.put("Tree.selectionForeground", TEXT);
        UIManager.put("Tree.selectionInactiveBackground", hex("#22301C"));
        UIManager.put("Tree.rowHeight", 24);
        UIManager.put("Table.background", PANEL);
        UIManager.put("Table.alternateRowColor", ALT_ROW);
        UIManager.put("Table.selectionBackground", SELECTION);
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("Table.selectionInactiveBackground", hex("#22301C"));
        UIManager.put("Table.gridColor", BORDER);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.rowHeight", 30);
        UIManager.put("Table.intercellSpacing", new Dimension(0, 1));
        UIManager.put("TableHeader.background", PANEL);
        UIManager.put("TableHeader.foreground", MUTED);
        UIManager.put("TableHeader.separatorColor", PANEL);
        UIManager.put("TableHeader.bottomSeparatorColor", STRONG_BORDER);
        UIManager.put("TableHeader.font", semibold(11.5f));
        UIManager.put("TableHeader.height", 32);
        // Tabs
        UIManager.put("TabbedPane.background", BACKGROUND);
        UIManager.put("TabbedPane.foreground", MUTED);
        UIManager.put("TabbedPane.selectedForeground", TEXT);
        UIManager.put("TabbedPane.selectedBackground", PANEL);
        UIManager.put("TabbedPane.hoverColor", HOVER);
        UIManager.put("TabbedPane.focusColor", PANEL);
        UIManager.put("TabbedPane.underlineColor", ACCENT);
        UIManager.put("TabbedPane.inactiveUnderlineColor", ACCENT_PRESSED);
        UIManager.put("TabbedPane.contentAreaColor", BORDER);
        UIManager.put("TabbedPane.tabSeparatorColor", BORDER);
        UIManager.put("TabbedPane.tabHeight", 36);
        UIManager.put("TabbedPane.showTabSeparators", true);
        // Scroll bars: slim, rounded thumbs
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.track", PANEL);
        UIManager.put("ScrollBar.thumb", STRONG_BORDER);
        UIManager.put("ScrollBar.hoverThumbColor", FAINT);
        UIManager.put("ScrollBar.pressedThumbColor", MUTED);
        UIManager.put("ScrollBar.showButtons", false);
        // Progress, tooltips, dialogs
        UIManager.put("ProgressBar.foreground", ACCENT);
        UIManager.put("ProgressBar.background", RAISED);
        UIManager.put("ProgressBar.selectionForeground", ACCENT_TEXT);
        UIManager.put("ProgressBar.selectionBackground", TEXT);
        UIManager.put("ToolTip.background", RAISED);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(STRONG_BORDER), BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("Label.disabledForeground", FAINT);
        FlatLaf.updateUI();
    }

    // ───────────── Component helpers ─────────────

    /** Small upper-case section heading, as MODerator's sidebar uses. */
    public static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text.toUpperCase());
        label.setFont(semibold(11));
        label.setForeground(MUTED);
        return label;
    }

    public static JLabel caption(String text) {
        JLabel label = new JLabel(text);
        label.setFont(ui(12));
        label.setForeground(MUTED);
        return label;
    }

    /** A caption that wraps to its width. */
    public static JTextArea wrappingCaption(String text) {
        JTextArea area = new JTextArea(text);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setBorder(BorderFactory.createEmptyBorder());
        area.setFont(ui(12));
        area.setForeground(MUTED);
        return area;
    }

    public static JLabel title(String text, float size) {
        JLabel label = new JLabel(text);
        label.setFont(semibold(size));
        label.setForeground(TEXT);
        return label;
    }

    /** A label showing one icon glyph (Segoe Fluent Icons code point). */
    public static JLabel icon(char glyph, float size, Color color) {
        JLabel label = new JLabel(hasIcons() ? String.valueOf(glyph) : "", SwingConstants.CENTER);
        label.setFont(icons(size));
        label.setForeground(color);
        return label;
    }

    /** The main action of a view: filled with the accent. */
    public static JButton accentButton(String text, char glyph) {
        JButton button = button(text, glyph);
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, "default");
        button.setForeground(ACCENT_TEXT);
        button.setPreferredSize(new Dimension(button.getPreferredSize().width, 40));
        return button;
    }

    /** A secondary button with an optional leading icon. */
    public static JButton button(String text, char glyph) {
        JButton button = new JButton(text);
        if (glyph != 0 && hasIcons()) {
            button.setIcon(new GlyphIcon(glyph, 13, null));
            button.setIconTextGap(8);
        }
        button.setFocusPainted(false);
        return button;
    }

    /** A compact square icon button. */
    public static JButton iconButton(char glyph, String tooltip) {
        JButton button = new JButton();
        if (hasIcons()) button.setIcon(new GlyphIcon(glyph, 14, null));
        else button.setText(tooltip.substring(0, 1));
        button.setToolTipText(tooltip);
        button.setFocusPainted(false);
        button.setMargin(new Insets(0, 0, 0, 0));
        Dimension size = new Dimension(34, 34);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
        return button;
    }

    /** Padding around a card's content. */
    public static Border pad(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    /** Marks a text field with placeholder text. */
    public static void placeholder(JComponent field, String text) {
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, text);
    }
}
