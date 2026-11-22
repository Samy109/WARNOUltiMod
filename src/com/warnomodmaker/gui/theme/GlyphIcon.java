package com.warnomodmaker.gui.theme;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** One Segoe Fluent Icons glyph as a Swing icon; it takes its component's text colour unless given one. */
public final class GlyphIcon implements Icon {
    private final String glyph;
    private final Font font;
    private final Color color;
    private final int size;

    public GlyphIcon(char glyph, float size, Color color) {
        this.glyph = String.valueOf(glyph);
        this.font = WarnoTheme.icons(size);
        this.color = color;
        this.size = Math.round(size);
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(font);
        Color paint = color != null ? color : c.getForeground();
        if (c instanceof AbstractButton && !c.isEnabled()) paint = WarnoTheme.FAINT;
        g2.setColor(paint);
        FontMetrics metrics = g2.getFontMetrics();
        int width = metrics.stringWidth(glyph);
        g2.drawString(glyph, x + (size - width) / 2, y + (size - metrics.getHeight()) / 2 + metrics.getAscent());
        g2.dispose();
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }
}
