package com.warnomodmaker;


import com.warnomodmaker.gui.MainWindow;

import javax.swing.*;
import java.awt.*;

/**
 * Main class for the WARNO Mod Maker application.
 * This application allows users to modify WARNO game files (NDF format).
 */
public class WarnoModMaker {
    
    /**
     * Application entry point
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        // Set the look and feel to the system look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Schedule a job for the event-dispatching thread to create and show the GUI
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
