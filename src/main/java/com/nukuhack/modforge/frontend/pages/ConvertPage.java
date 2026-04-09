package com.nukuhack.modforge.frontend.pages;

import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.backend.service.IconService;
import com.nukuhack.modforge.frontend.BarManager;
import com.nukuhack.modforge.frontend.MainWindow;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CompletableFuture;

// =============================================================================
//  IMAGE CONVERT PAGE
// =============================================================================
@lombok.extern.slf4j.Slf4j
public class ConvertPage extends BasePage {
	
	// ── UI refs ──────────────────────────────────────────────────────────────
	private final JLabel pathLabel;
	private final JButton goBtn;
	private final JButton folderBtn;
	private final JButton toggleBtn;
	// ── State ────────────────────────────────────────────────────────────────
	private String selectedPath = null;
	/** true = DDS→PNG (default), false = PNG→DDS */
	private boolean ddsToPng = true;
	
	// ── Constructor ──────────────────────────────────────────────────────────
	public ConvertPage(MainWindow w) {
		super(w);
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
		setLayout(new BorderLayout(0, 16));
		
		// ── Top header ──────────────────────────────────────────────────────
		add(header("Convert Textures"), BorderLayout.NORTH);
		
		// ── Center card ─────────────────────────────────────────────────────
		// card() uses BorderLayout internally and puts the title in NORTH.
		// We must NOT override card's layout — instead put our content in CENTER.
		JPanel card = card("DDS  ↔  PNG");
		
		// Inner content panel with GridBagLayout — sits in card's CENTER slot
		JPanel content = new JPanel(new GridBagLayout());
		content.setOpaque(false);
		
		GridBagConstraints gc = new GridBagConstraints();
		gc.fill = GridBagConstraints.HORIZONTAL;
		gc.insets = new Insets(10, 4, 10, 4);
		
		// ── Row 0: description ───────────────────────────────────────────────
		gc.gridx = 0;
		gc.gridy = 0;
		gc.gridwidth = 4;
		gc.weightx = 1.0;
		JLabel desc = muted("Select a file, folder, or archive (.pak / .zip) to convert.");
		content.add(desc, gc);
		
		// ── Row 1: Browse button + path label ────────────────────────────────
		gc.gridy = 1;
		gc.gridwidth = 1;
		
		gc.gridx = 0;
		gc.weightx = 0;
		JButton browseBtn = primaryBtn("Browse…", e -> pickPath());
		browseBtn.setPreferredSize(new Dimension(110, 32));
		content.add(browseBtn, gc);
		
		gc.gridx = 1;
		gc.weightx = 1.0;
		gc.gridwidth = 3;
		pathLabel = new JLabel("No path selected");
		pathLabel.setForeground(MainWindow.MUTED);
		pathLabel.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
		pathLabel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x2a2a3a), 1), BorderFactory.createEmptyBorder(6, 10, 6, 10)));
		pathLabel.setOpaque(true);
		pathLabel.setBackground(new Color(0x1e1e2e));
		content.add(pathLabel, gc);
		
		// ── Row 2: Toggle + spacer + GO ──────────────────────────────────────
		gc.gridy = 2;
		gc.gridwidth = 1;
		gc.weightx = 0;
		
		gc.gridx = 0;
		toggleBtn = new JButton(modeLabel());
		styleButton(toggleBtn);
		toggleBtn.addActionListener(e -> {
			ddsToPng = ! ddsToPng;
			toggleBtn.setText(modeLabel());
		});
		content.add(toggleBtn, gc);
		
		// open-folder button
		gc.gridx = 1;
		gc.weightx = 0;
		folderBtn = new JButton("Open Folder");
		styleButton(folderBtn);
		folderBtn.addActionListener(e -> openSelectedFolder());
		folderBtn.setBackground(new Color(0x313244));
		folderBtn.setForeground(MainWindow.ACCENT);
		folderBtn.setFont(new Font("Roboto", Font.PLAIN, 12));
		content.add(folderBtn, gc);
		
		// spacer column stretches to push GO to the right
		gc.gridx = 2;
		gc.weightx = 1.0;
		content.add(Box.createHorizontalGlue(), gc);
		
		gc.gridx = 3;
		gc.weightx = 0;
		goBtn = primaryBtn("GO", e -> runConversion());
		goBtn.setPreferredSize(new Dimension(90, 36));
		goBtn.setFont(new Font("Roboto", Font.BOLD, 15));
		setBtnEnabled(false); // disabled until path is chosen
		content.add(goBtn, gc);
		
		// ── Filler row: pushes content to the top inside the card ────────────
		gc.gridx = 0;
		gc.gridy = 3;
		gc.gridwidth = 4;
		gc.weightx = 1.0;
		gc.weighty = 1.0;
		gc.fill = GridBagConstraints.BOTH;
		content.add(Box.createVerticalGlue(), gc);
		
		card.add(content, BorderLayout.CENTER);
		add(card, BorderLayout.CENTER);
		
		// ── Bottom: back ─────────────────────────────────────────────────────
		JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		bottom.setOpaque(false);
		bottom.add(primaryBtn("← Back", e -> window.navigate(MainWindow.Page.HOME)));
		add(bottom, BorderLayout.SOUTH);
	}
	
	// ── BasePage contract ─────────────────────────────────────────────────────
	@Override
	public void refresh(Object... input) {
		// nothing to restore — page is stateless between navigations
	}
	
	// ── Internal helpers ──────────────────────────────────────────────────────
	
	private String modeLabel() {
		return ddsToPng ? "⇄  DDS → PNG" : "⇄  PNG → DDS";
	}
	
	private void styleButton(JButton b) {
		b.setBackground(new Color(0x313244));
		b.setForeground(MainWindow.ACCENT);
		b.setFocusPainted(false);
		b.setBorderPainted(false);
		b.setFont(new Font("Roboto", Font.BOLD, 12));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.setPreferredSize(new Dimension(160, 32));
	}
	
	private void setBtnEnabled(boolean enabled) {
		goBtn.setEnabled(enabled);
		goBtn.setBackground(enabled ? MainWindow.ACCENT : new Color(0x313244));
		Color foreground = enabled ? new Color(0x1e1e2e) : new Color(0x6c6f85);
		goBtn.setForeground(foreground);
		
		folderBtn.setEnabled(enabled);
		folderBtn.setBackground(enabled ? MainWindow.ACCENT : new Color(0x313244));
		folderBtn.setForeground(foreground);
	}
	
	/** Opens the selected path in the file manager; if it's a file, opens its parent folder. */
	private void openSelectedFolder() {
		if (selectedPath == null || selectedPath.isBlank())
			return;
		java.io.File f = new java.io.File(selectedPath);
		String dirToOpen = f.isDirectory() ? f.getAbsolutePath() : f.getParent();
		Util.openDirectory(this, dirToOpen);
	}
	
	/** Open folder/file chooser (re-uses Util.pickFolderAsync style, but also allows files). */
	private void pickPath() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Select file, folder, or archive to convert");
		chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		chooser.setMultiSelectionEnabled(false);
		
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			selectedPath = chooser.getSelectedFile().getAbsolutePath();
			pathLabel.setText(selectedPath);
			pathLabel.setForeground(MainWindow.TEXT);
			setBtnEnabled(true);
		}
	}
	
	/** Run the conversion on a background thread to keep the UI responsive. */
	private void runConversion() {
		if (selectedPath == null || selectedPath.isBlank())
			return;
		
		goBtn.setEnabled(false);
		goBtn.setText("…");
		
		final boolean toPng = this.ddsToPng;
		final String path = this.selectedPath;
		
		CompletableFuture.runAsync(() -> {
			try {
				IconService.convertImages(path, toPng);
				SwingUtilities.invokeLater(() -> showResult(true, toPng, null));
			} catch (Exception ex) {
				SwingUtilities.invokeLater(() -> showResult(false, toPng, ex.getMessage()));
			}
		});
	}
	
	private void showResult(boolean success, boolean wasToPng, String errorMsg) {
		// Restore button
		goBtn.setText("GO");
		setBtnEnabled(selectedPath != null);
		
		String direction = wasToPng ? "DDS → PNG" : "PNG → DDS";
		
		if (success) {
			window.snackbar.show("Conversion complete (" + direction + ")", BarManager.Type.SUCCESS);
			
			JOptionPane.showMessageDialog(this, "<html><b>Conversion complete!</b><br/><br/>" + "Direction: <tt>" + direction + "</tt><br/>" + "Source: <tt>" + selectedPath + "</tt><br/><br/>" + "Output was written next to the source (archives get a <tt>_converted/</tt> folder).</html>", "Done", JOptionPane.INFORMATION_MESSAGE);
		} else {
			window.snackbar.show("Conversion failed", BarManager.Type.ERROR);
			
			String reason = (errorMsg != null && ! errorMsg.isBlank()) ? errorMsg : "Unknown error — check the application log for details.";
			
			JOptionPane.showMessageDialog(this, "<html><b>Conversion failed.</b><br/><br/>" + "Direction: <tt>" + direction + "</tt><br/>" + "Source: <tt>" + selectedPath + "</tt><br/><br/>" + "<b>Reason:</b><br/><tt>" + reason + "</tt></html>", "Conversion Error", JOptionPane.ERROR_MESSAGE);
		}
	}
}