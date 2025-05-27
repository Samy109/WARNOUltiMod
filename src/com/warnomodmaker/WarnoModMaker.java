package com.warnomodmaker;

import com.formdev.flatlaf.FlatDarkLaf;
import com.warnomodmaker.gui.MainWindow;
import com.warnomodmaker.gui.theme.WarnoTheme;

import javax.swing.*;
import java.awt.*;

public class WarnoModMaker {

    public static void main(String[] args) {

        try {
            // Try to use our custom theme first, fallback to FlatDarkLaf
            if (!WarnoTheme.setup()) {
                FlatDarkLaf.setup();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            try {
                MainWindow mainWindow = new MainWindow();
                mainWindow.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null,
                    "Error starting application: " + e.getMessage(),
                    "Application Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
