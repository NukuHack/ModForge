package com.nukuhack.modforge.frontend.pages;

import com.nukuhack.image.ConversionOptions;
import com.nukuhack.image.ImageConverter;
import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.frontend.BarManager;
import com.nukuhack.modforge.frontend.MainWindow;
import com.nukuhack.modforge.frontend.Page;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.nukuhack.modforge.frontend.MainWindow.getLocalText;

@Slf4j
public class KCDConverterPage extends BasePage {
	
	private final JTextField inputField = styledField("ui_kcd_input_label");
	private final JTextField outputField = styledField("ui_kcd_output_label");
	
	private final JCheckBox saveRawCheck = new JCheckBox(getLocalText("ui_kcd_save_raw"));
	private final JCheckBox separateGlossCheck = new JCheckBox(getLocalText("ui_kcd_separate_gloss"));
	private final JCheckBox deleteSourceCheck = new JCheckBox(getLocalText("ui_kcd_delete_source"));
	private final JCheckBox recursiveCheck = new JCheckBox(getLocalText("ui_kcd_recursive"));
	
	private final JTextArea logArea = new JTextArea();
	private final JButton convertBtn;
	
	public KCDConverterPage(MainWindow w) {
		super(w);
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
		setLayout(new BorderLayout(0, 16));
		
		convertBtn = primaryBtn("ui_kcd_convert", e -> startConversion());
		convertBtn.setFont(convertBtn.getFont().deriveFont(Font.BOLD, 14f));
		
		add(header("ui_kcd_title"), BorderLayout.NORTH);
		add(buildCenter(), BorderLayout.CENTER);
		add(buildBottom(), BorderLayout.SOUTH);
	}
	
	private JPanel buildCenter() {
		JPanel outer = new JPanel(new BorderLayout(0, 12));
		outer.setOpaque(false);
		outer.add(buildFormCard(), BorderLayout.NORTH);
		outer.add(buildLogCard(), BorderLayout.CENTER);
		return outer;
	}
	
	private JPanel buildFormCard() {
		JPanel card = card(null);
		card.setLayout(new GridBagLayout());
		
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = new Insets(6, 6, 6, 6);
		
		gbc.gridy = 0;
		gbc.gridx = 0;
		gbc.weightx = 0;
		card.add(muted("ui_kcd_input_label"), gbc);
		
		gbc.gridx = 1;
		gbc.weightx = 1.0;
		card.add(inputField, gbc);
		
		gbc.gridx = 2;
		gbc.weightx = 0;
		card.add(primaryBtn("ui_kcd_file", e -> Util.pickFileAsync(getLocalText("ui_kcd_select_dds_file"), ".dds", "DDS files").thenAccept(p -> {
			if (p != null)
				inputField.setText(p);
		})), gbc);
		
		gbc.gridx = 3;
		card.add(primaryBtn("ui_kcd_folder", e -> Util.pickFolderAsync().thenAccept(p -> {
			if (p != null)
				inputField.setText(p);
		})), gbc);
		
		gbc.gridy = 1;
		gbc.gridx = 0;
		gbc.weightx = 0;
		card.add(muted("ui_kcd_output_label"), gbc);
		
		gbc.gridx = 1;
		gbc.weightx = 1.0;
		card.add(outputField, gbc);
		
		gbc.gridx = 2;
		gbc.weightx = 0;
		card.add(primaryBtn("ui_kcd_file", e -> Util.pickFileAsync(getLocalText("ui_kcd_save_tiff_as"), ".tif", "TIFF files").thenAccept(p -> {
			if (p != null)
				outputField.setText(p);
		})), gbc);
		
		gbc.gridx = 3;
		card.add(primaryBtn("ui_kcd_folder", e -> Util.pickFolderAsync().thenAccept(p -> {
			if (p != null)
				outputField.setText(p);
		})), gbc);
		
		gbc.gridy = 2;
		gbc.gridx = 0;
		gbc.gridwidth = 4;
		
		JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
		optionsPanel.setOpaque(false);
		optionsPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(0x313244)), getLocalText("ui_kcd_options"), javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP, new Font("Roboto", Font.BOLD, 12), MainWindow.ACCENT));
		styleCheck(saveRawCheck);
		styleCheck(separateGlossCheck);
		styleCheck(deleteSourceCheck);
		styleCheck(recursiveCheck);
		optionsPanel.add(saveRawCheck);
		optionsPanel.add(separateGlossCheck);
		optionsPanel.add(deleteSourceCheck);
		optionsPanel.add(recursiveCheck);
		card.add(optionsPanel, gbc);
		
		gbc.gridy = 3;
		gbc.gridwidth = 4;
		gbc.fill = GridBagConstraints.NONE;
		gbc.anchor = GridBagConstraints.EAST;
		card.add(convertBtn, gbc);
		
		return card;
	}
	
	private JPanel buildLogCard() {
		JPanel card = card("ui_kcd_log");
		
		logArea.setEditable(false);
		logArea.setBackground(new Color(0x181825));
		logArea.setForeground(MainWindow.TEXT);
		logArea.setCaretColor(MainWindow.TEXT);
		logArea.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
		logArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		
		JScrollPane scroll = new JScrollPane(logArea);
		scroll.setBackground(new Color(0x181825));
		scroll.getViewport().setBackground(new Color(0x181825));
		scroll.setBorder(BorderFactory.createLineBorder(new Color(0x313244)));
		card.add(scroll, BorderLayout.CENTER);
		
		return card;
	}
	
	private JPanel buildBottom() {
		JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		bar.setOpaque(false);
		bar.add(primaryBtn("ui_back", e -> window.navigate(Page.HOME)));
		return bar;
	}
	
	private void startConversion() {
		String input = inputField.getText().trim();
		String output = outputField.getText().trim();
		
		String inputPlaceholder = getLocalText("ui_kcd_input_label");
		String outputPlaceholder = getLocalText("ui_kcd_output_label");
		if (input.equals(inputPlaceholder))
			input = "";
		if (output.equals(outputPlaceholder))
			output = "";
		
		if (input.isEmpty() || output.isEmpty()) {
			window.snackbar.show("ui_kcd_missing_paths", BarManager.Type.WARNING);
			return;
		}
		
		convertBtn.setEnabled(false);
		logArea.setText("");
		
		final String finalInput = input;
		final String finalOutput = output;
		
		executor.submit(() -> {
			try {
				appendLog(getLocalText("ui_kcd_log_starting"));
				appendLog(getLocalText("ui_kcd_log_input", finalInput));
				appendLog(getLocalText("ui_kcd_log_output", finalOutput));
				
				Path inputPath = Path.of(finalInput);
				Path outputPath = Path.of(finalOutput);
				
				boolean isOutputFolder = ! finalOutput.toLowerCase().endsWith(".tif");
				
				var opts = new ConversionOptions().saveRawDDS(saveRawCheck.isSelected()).separateGlossMap(separateGlossCheck.isSelected()).deleteSourceFiles(deleteSourceCheck.isSelected()).outputPath(finalOutput).isOutputFolder(isOutputFolder);
				
				if (Files.isDirectory(inputPath)) {
					appendLog(getLocalText("ui_kcd_log_batch", inputPath, recursiveCheck.isSelected()));
					var futures = ImageConverter.batchProcess(inputPath, outputPath, opts, recursiveCheck.isSelected());
					ImageConverter.awaitAll(futures);
				} else if (Files.isRegularFile(inputPath) && inputPath.toString().toLowerCase().endsWith(".dds")) {
					appendLog(getLocalText("ui_kcd_log_single", inputPath));
					ImageConverter.convertImage(inputPath, opts);
				} else {
					appendLog(getLocalText("ui_kcd_log_invalid_input", inputPath));
					SwingUtilities.invokeLater(() -> window.snackbar.show("ui_kcd_log_invalid_input", BarManager.Type.ERROR, inputPath));
					return;
				}
				
				appendLog(getLocalText("ui_kcd_log_done"));
				SwingUtilities.invokeLater(() -> window.snackbar.show("ui_kcd_success_message", BarManager.Type.SUCCESS));
				
			} catch (Exception e) {
				log.warn("Conversion failed", Util.limitStackTrace(e, 10));
				appendLog(getLocalText("ui_kcd_log_error", e.getMessage()));
				SwingUtilities.invokeLater(() -> window.snackbar.show("ui_kcd_failed_message", BarManager.Type.ERROR, e.getMessage()));
			} finally {
				SwingUtilities.invokeLater(() -> convertBtn.setEnabled(true));
			}
		});
	}
	
	private void appendLog(String message) {
		SwingUtilities.invokeLater(() -> {
			logArea.append(message + "\n");
			logArea.setCaretPosition(logArea.getDocument().getLength());
		});
	}
	
	private static void styleCheck(JCheckBox cb) {
		cb.setBackground(new Color(0x181825));
		cb.setForeground(MainWindow.TEXT);
		cb.setFont(new Font("Roboto", Font.PLAIN, 13));
		cb.setFocusPainted(false);
	}
}