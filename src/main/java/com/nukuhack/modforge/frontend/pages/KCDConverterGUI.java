package com.nukuhack.modforge.frontend.pages;

import com.nukuhack.image.ConversionOptions;
import com.nukuhack.image.ImageConverter;
import lombok.extern.slf4j.Slf4j;
import com.nukuhack.modforge.Util;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class KCDConverterGUI extends JFrame {
    
    private final JTextField inputField;
    private final JTextField outputField;
    private final JCheckBox saveRawCheck;
    private final JCheckBox separateGlossCheck;
    private final JCheckBox deleteSourceCheck;
    private final JCheckBox recursiveCheck;
    private final JTextArea logArea;
    private final JButton convertButton;
    
    public KCDConverterGUI() {
        setTitle("KCD Texture Exporter");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        
        // Main panel with padding
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // Input selection
        gbc.gridx = 0; gbc.gridy = 0;
        mainPanel.add(new JLabel("Input (DDS file or folder):"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0;
        inputField = new JTextField(30);
        mainPanel.add(inputField, gbc);
        
        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0;
        JButton inputFileBtn = new JButton("File...");
        inputFileBtn.addActionListener(this::pickInputFile);
        mainPanel.add(inputFileBtn, gbc);
        
        gbc.gridx = 3; gbc.gridy = 0;
        JButton inputFolderBtn = new JButton("Folder...");
        inputFolderBtn.addActionListener(this::pickInputFolder);
        mainPanel.add(inputFolderBtn, gbc);
        
        // Output selection
        gbc.gridx = 0; gbc.gridy = 1;
        mainPanel.add(new JLabel("Output (TIFF file or folder):"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0;
        outputField = new JTextField(30);
        mainPanel.add(outputField, gbc);
        
        gbc.gridx = 2; gbc.gridy = 1; gbc.weightx = 0;
        JButton outputFileBtn = new JButton("File...");
        outputFileBtn.addActionListener(this::pickOutputFile);
        mainPanel.add(outputFileBtn, gbc);
        
        gbc.gridx = 3; gbc.gridy = 1;
        JButton outputFolderBtn = new JButton("Folder...");
        outputFolderBtn.addActionListener(this::pickOutputFolder);
        mainPanel.add(outputFolderBtn, gbc);
		
		
		// Add the mouse listener
		mainPanel.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				// The key line: getButton() will return 4, 5, 6, etc.
				int button = e.getButton();
				log.debug("Button {} pressed.", button);
			}
		});
        
        // Options panel
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
        optionsPanel.setBorder(BorderFactory.createTitledBorder("Options"));
        saveRawCheck = new JCheckBox("Save raw DDS");
        separateGlossCheck = new JCheckBox("Separate gloss map");
        deleteSourceCheck = new JCheckBox("Delete source files");
        recursiveCheck = new JCheckBox("Process subfolders");
        optionsPanel.add(saveRawCheck);
        optionsPanel.add(separateGlossCheck);
        optionsPanel.add(deleteSourceCheck);
        optionsPanel.add(recursiveCheck);
        
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 4;
        mainPanel.add(optionsPanel, gbc);
        
        // Convert button
        gbc.gridy = 3; gbc.gridwidth = 4;
        convertButton = new JButton("Convert");
        convertButton.setFont(convertButton.getFont().deriveFont(Font.BOLD, 14f));
        convertButton.addActionListener(this::startConversion);
        mainPanel.add(convertButton, gbc);
        
        // Log area
        logArea = new JTextArea(10, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Log"));
        
        gbc.gridy = 4; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH;
        mainPanel.add(scrollPane, gbc);
        
        add(mainPanel, BorderLayout.CENTER);
        
        pack();
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(700, 500));
    }
    
    private void pickInputFile(ActionEvent e) {
		Util.pickFileAsync("Select DDS file", ".dds", "DDS files")
            .thenAccept(path -> {
                if (path != null) inputField.setText(path);
            });
    }
    
    private void pickInputFolder(ActionEvent e) {
        Util.pickFolderAsync().thenAccept(path -> {
            if (path != null) inputField.setText(path);
        });
    }
    
    private void pickOutputFile(ActionEvent e) {
		Util.pickFileAsync("Save TIFF file as", ".tif", "TIFF files")
            .thenAccept(path -> {
                if (path != null) outputField.setText(path);
            });
    }
    
    private void pickOutputFolder(ActionEvent e) {
		Util.pickFolderAsync().thenAccept(path -> {
            if (path != null) outputField.setText(path);
        });
    }
    
    private void startConversion(ActionEvent e) {
        String input = inputField.getText().trim();
        String output = outputField.getText().trim();
        
        if (input.isEmpty() || output.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select both input and output paths.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // Disable UI during conversion
        convertButton.setEnabled(false);
        logArea.setText("");
        
        // Run conversion in background
        CompletableFuture.runAsync(() -> {
            try {
                appendLog("Starting conversion...");
                appendLog("Input: " + input);
                appendLog("Output: " + output);
                
                Path inputPath = Path.of(input);
                Path outputPath = Path.of(output);
                
                boolean isOutputFolder = !output.toLowerCase().endsWith(".tif");
                
                var opts = new ConversionOptions()
                    .saveRawDDS(saveRawCheck.isSelected())
                    .separateGlossMap(separateGlossCheck.isSelected())
                    .deleteSourceFiles(deleteSourceCheck.isSelected())
                    .outputPath(output)
                    .isOutputFolder(isOutputFolder);
                
                if (Files.isDirectory(inputPath)) {
                    appendLog("Batch converting " + inputPath + " (recursive: " + recursiveCheck.isSelected() + ")");
                    var futures = ImageConverter.batchProcess(inputPath, outputPath, opts, recursiveCheck.isSelected());
                    ImageConverter.awaitAll(futures);
                } else if (Files.isRegularFile(inputPath) && inputPath.toString().toLowerCase().endsWith(".dds")) {
                    appendLog("Converting single file: " + inputPath);
                    ImageConverter.convertImage(inputPath, opts);
                } else {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, 
                            "Input path is not a .dds file or a directory: " + inputPath, 
                            "Error", JOptionPane.ERROR_MESSAGE);
                    });
                    return;
                }
                
                appendLog("Conversion completed successfully!");
                
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Conversion completed!", "Success", JOptionPane.INFORMATION_MESSAGE);
                });
                
            } catch (Exception ex) {
                log.error("Conversion failed", ex);
                appendLog("ERROR: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, 
                        "Conversion failed: " + ex.getMessage(), 
                        "Error", JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                SwingUtilities.invokeLater(() -> convertButton.setEnabled(true));
            }
        });
    }
    
    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}