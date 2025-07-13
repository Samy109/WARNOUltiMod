package test.java.com.warnomodmaker;

import com.warnomodmaker.gui.theme.WarnoTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;

/**
 * Debug console window that captures System.out and System.err output
 */
public class DebugConsoleWindow extends JFrame {
    
    private JTextArea consoleArea;
    private JScrollPane scrollPane;
    private PrintStream originalOut;
    private PrintStream originalErr;
    private boolean isCapturing = false;
    
    public DebugConsoleWindow() {
        super("Debug Console - WARNO Mod Maker E2E Tests");
        initializeUI();
        setupStreams();
        
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
    }
    
    private void initializeUI() {
        setLayout(new BorderLayout());
        
        // Console text area
        consoleArea = new JTextArea();
        consoleArea.setEditable(false);
        consoleArea.setBackground(Color.BLACK);
        consoleArea.setForeground(Color.GREEN);
        consoleArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        consoleArea.setCaretColor(Color.GREEN);
        
        scrollPane = new JScrollPane(consoleArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        
        add(scrollPane, BorderLayout.CENTER);
        
        // Control panel
        JPanel controlPanel = createControlPanel();
        add(controlPanel, BorderLayout.SOUTH);
        
        // Initial message
        appendToConsole("=== Debug Console Initialized ===\n");
        appendToConsole("This window captures all System.out and System.err output from the test execution.\n");
        appendToConsole("Use the controls below to manage console output.\n\n");
    }
    
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout());
        panel.setBackground(WarnoTheme.TACTICAL_GREEN_DARK);
        panel.setBorder(BorderFactory.createRaisedBevelBorder());
        
        JButton clearButton = new JButton("Clear Console");
        clearButton.setBackground(WarnoTheme.WARNING_ORANGE);
        clearButton.setForeground(Color.WHITE);
        clearButton.addActionListener(e -> clearConsole());
        
        JButton saveButton = new JButton("Save to File");
        saveButton.setBackground(WarnoTheme.ACCENT_BLUE);
        saveButton.setForeground(Color.WHITE);
        saveButton.addActionListener(e -> saveConsoleToFile());
        
        JToggleButton captureButton = new JToggleButton("Capture Output", true);
        captureButton.setBackground(WarnoTheme.SUCCESS_GREEN);
        captureButton.setForeground(Color.WHITE);
        captureButton.addActionListener(e -> toggleCapture(captureButton.isSelected()));
        
        JButton scrollToBottomButton = new JButton("Scroll to Bottom");
        scrollToBottomButton.setBackground(WarnoTheme.TACTICAL_GREEN_LIGHT);
        scrollToBottomButton.setForeground(Color.WHITE);
        scrollToBottomButton.addActionListener(e -> scrollToBottom());
        
        panel.add(clearButton);
        panel.add(saveButton);
        panel.add(captureButton);
        panel.add(scrollToBottomButton);
        
        return panel;
    }
    
    private void setupStreams() {
        // Store original streams
        originalOut = System.out;
        originalErr = System.err;
        
        // Create custom print streams that capture output
        System.setOut(new PrintStream(new ConsoleOutputStream(originalOut, false)));
        System.setErr(new PrintStream(new ConsoleOutputStream(originalErr, true)));
        
        isCapturing = true;
    }
    
    private void restoreStreams() {
        if (originalOut != null) {
            System.setOut(originalOut);
        }
        if (originalErr != null) {
            System.setErr(originalErr);
        }
        isCapturing = false;
    }
    
    private void toggleCapture(boolean capture) {
        if (capture && !isCapturing) {
            setupStreams();
            appendToConsole("\n=== Output capture resumed ===\n");
        } else if (!capture && isCapturing) {
            restoreStreams();
            appendToConsole("\n=== Output capture paused ===\n");
        }
    }
    
    private void clearConsole() {
        SwingUtilities.invokeLater(() -> {
            consoleArea.setText("");
            appendToConsole("=== Console cleared ===\n");
        });
    }
    
    private void saveConsoleToFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File("debug-console-" + System.currentTimeMillis() + ".txt"));
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (PrintWriter writer = new PrintWriter(fileChooser.getSelectedFile())) {
                writer.println("WARNO Mod Maker E2E Test Debug Console Output");
                writer.println("Generated: " + new java.util.Date());
                writer.println("=" + "=".repeat(60));
                writer.println();
                writer.print(consoleArea.getText());
                
                JOptionPane.showMessageDialog(this, 
                    "Console output saved to: " + fileChooser.getSelectedFile().getName(),
                    "Save Successful", 
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, 
                    "Failed to save console output: " + e.getMessage(),
                    "Save Failed", 
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
        });
    }
    
    public void appendToConsole(String text) {
        SwingUtilities.invokeLater(() -> {
            consoleArea.append(text);
            // Auto-scroll to bottom
            consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
        });
    }
    
    @Override
    public void dispose() {
        // Restore original streams when window is disposed
        restoreStreams();
        super.dispose();
    }
    
    /**
     * Custom OutputStream that captures console output and displays it in the debug window
     */
    private class ConsoleOutputStream extends OutputStream {
        private final PrintStream originalStream;
        private final boolean isError;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        
        public ConsoleOutputStream(PrintStream originalStream, boolean isError) {
            this.originalStream = originalStream;
            this.isError = isError;
        }
        
        @Override
        public void write(int b) throws IOException {
            buffer.write(b);
            
            // If we hit a newline, flush the buffer
            if (b == '\n') {
                flush();
            }
        }
        
        @Override
        public void flush() throws IOException {
            String text = buffer.toString();
            if (!text.isEmpty()) {
                // Send to original stream (so it still appears in IDE console if needed)
                originalStream.print(text);
                originalStream.flush();
                
                // Send to our debug console
                if (isCapturing) {
                    if (isError) {
                        // Add error prefix for stderr
                        appendToConsole("[ERROR] " + text);
                    } else {
                        appendToConsole(text);
                    }
                }
                
                buffer.reset();
            }
        }
    }
}
