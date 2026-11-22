package com.warnomodmaker.gui.theme;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/** A card: a rounded, filled panel with a one-pixel border. */
public class RoundedPanel extends JPanel {
    private Color fill;
    private Color stroke;
    private final int radius;

    public RoundedPanel(LayoutManager layout, Color fill, Color stroke, int radius) {
        super(layout);
        this.fill = fill;
        this.stroke = stroke;
        this.radius = radius;
        setOpaque(false);
    }

    /** A standard card on the page background. */
    public static RoundedPanel card(LayoutManager layout) {
        RoundedPanel panel = new RoundedPanel(layout, WarnoTheme.PANEL, WarnoTheme.BORDER, 10);
        panel.setBorder(WarnoTheme.pad(16, 16, 16, 16));
        return panel;
    }

    public void setColors(Color fill, Color stroke) {
        this.fill = fill;
        this.stroke = stroke;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, radius * 2f, radius * 2f);
        if (fill != null) {
            g2.setColor(fill);
            g2.fill(shape);
        }
        if (stroke != null) {
            g2.setColor(stroke);
            g2.draw(shape);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
