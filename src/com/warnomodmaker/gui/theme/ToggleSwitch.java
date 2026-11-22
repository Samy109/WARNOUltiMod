package com.warnomodmaker.gui.theme;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** A check box drawn as a switch: a rounded track with a knob that slides right and fills with the accent when on. */
public final class ToggleSwitch extends JCheckBox {
    public ToggleSwitch(String text) {
        super(text);
        setOpaque(false);
        setFocusPainted(false);
        setIconTextGap(8);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        Icon track = new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean on = isSelected();
                Color fill = on ? WarnoTheme.ACCENT : WarnoTheme.INPUT;
                Color border = on ? WarnoTheme.ACCENT : WarnoTheme.STRONG_BORDER;
                if (!isEnabled()) fill = fill.darker();
                g2.setColor(fill);
                g2.fillRoundRect(x, y, 34, 18, 18, 18);
                g2.setColor(border);
                g2.drawRoundRect(x, y, 33, 17, 18, 18);
                g2.setColor(on ? WarnoTheme.ACCENT_TEXT : WarnoTheme.MUTED);
                g2.fillOval(on ? x + 34 - 14 : x + 4, y + 4, 10, 10);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return 34;
            }

            @Override
            public int getIconHeight() {
                return 18;
            }
        };
        setIcon(track);
        setSelectedIcon(track);
        setRolloverIcon(track);
        setRolloverSelectedIcon(track);
        setPressedIcon(track);
        setDisabledIcon(track);
        setDisabledSelectedIcon(track);
    }
}
