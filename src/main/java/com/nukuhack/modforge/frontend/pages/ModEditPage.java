package com.nukuhack.modforge.frontend.pages;

import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.backend.ModData;
import com.nukuhack.modforge.backend.service.ConfigService;
import com.nukuhack.modforge.backend.service.ModService;
import com.nukuhack.modforge.frontend.BarManager;
import com.nukuhack.modforge.frontend.MainWindow;
import com.nukuhack.util.IOUtil;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@Slf4j
public class ModEditPage extends BasePage {
	
	private final List<JTextField> versionFields = new ArrayList<>();
	private ModData currentMod;
	
	private JTextField idField;
	private JTextField nameField;
	private JTextArea descriptionArea;
	private JTextField authorField;
	private JTextField versionField;
	private JTextField createdOnField;
	private JCheckBox modifiesLevelCheck;
	private JPanel versionsPanel;
	
	public ModEditPage(MainWindow w) {
		super(w);
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
		
		setLayout(new BorderLayout(0, 16));
		add(header("ui_mod_editor"), BorderLayout.NORTH);
		
		JScrollPane scrollPane = new JScrollPane(buildForm());
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.getViewport().setBackground(MainWindow.BG);
		scrollPane.setBackground(MainWindow.BG);
		add(scrollPane, BorderLayout.CENTER);
		
		JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
		bottomBar.setOpaque(false);
		bottomBar.add(primaryBtn("ui_save_changes", e -> saveManifest(window.getRegistry().userConfig.getGameDir())));
		bottomBar.add(primaryBtn("ui_mod_export", e -> exportMod()));
		bottomBar.add(primaryBtn("ui_mod_delete", e -> deleteMod()));
		bottomBar.add(primaryBtn("ui_open_folder", e -> openFolder()));
		bottomBar.add(primaryBtn("ui_back", e -> window.navigate(MainWindow.Page.MODS)));
		add(bottomBar, BorderLayout.SOUTH);
	}
	
	@Override
	public void refresh(Object... input) {
		if (input.length > 0 && input[0] instanceof ModData mod)
			this.refreshFieldData(mod);
		else
			window.navigate(MainWindow.Page.HOME);
	}
	
	private JPanel buildForm() {
		JPanel form = new JPanel();
		form.setLayout(new GridBagLayout());
		form.setOpaque(false);
		form.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		
		GridBagConstraints gc = new GridBagConstraints();
		gc.insets = new Insets(8, 12, 8, 12);
		gc.fill = GridBagConstraints.HORIZONTAL;
		
		addFormRow(form, gc, "ui_mod_id", idField = styledField(""), 0);
		addFormRow(form, gc, "ui_mod_name", nameField = styledField(""), 1);
		addFormRow(form, gc, "ui_mod_author", authorField = styledField(""), 2);
		addFormRow(form, gc, "ui_mod_version", versionField = styledField(""), 3);
		addFormRow(form, gc, "ui_mod_creation", createdOnField = styledField(""), 4);
		
		gc.gridy = 5;
		gc.gridx = 0;
		gc.weightx = 0.2;
		JLabel descLabel = new JLabel(MainWindow.getLocalText("ui_mod_description"));
		descLabel.setForeground(MainWindow.TEXT);
		descLabel.setFont(new Font("Roboto", Font.PLAIN, 13));
		form.add(descLabel, gc);
		
		gc.gridx = 1;
		gc.weightx = 0.8;
		descriptionArea = new JTextArea(4, 30);
		descriptionArea.setText("");
		descriptionArea.setBackground(new Color(0x313244));
		descriptionArea.setForeground(MainWindow.TEXT);
		descriptionArea.setCaretColor(MainWindow.TEXT);
		descriptionArea.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x45475a), 1), BorderFactory.createEmptyBorder(6, 10, 6, 10)));
		descriptionArea.setFont(new Font("Roboto", Font.PLAIN, 13));
		descriptionArea.setLineWrap(true);
		descriptionArea.setWrapStyleWord(true);
		
		JScrollPane descScroll = new JScrollPane(descriptionArea);
		descScroll.setBackground(new Color(0x313244));
		descScroll.setBorder(null);
		form.add(descScroll, gc);
		
		gc.gridy = 6;
		gc.gridx = 0;
		gc.weightx = 0.2;
		JLabel levelLabel = new JLabel(MainWindow.getLocalText("ui_mod_modifies_level"));
		levelLabel.setForeground(MainWindow.TEXT);
		levelLabel.setFont(new Font("Roboto", Font.PLAIN, 13));
		form.add(levelLabel, gc);
		
		gc.gridx = 1;
		modifiesLevelCheck = new JCheckBox();
		modifiesLevelCheck.setSelected(false);
		modifiesLevelCheck.setBackground(new Color(0x313244));
		modifiesLevelCheck.setForeground(MainWindow.TEXT);
		form.add(modifiesLevelCheck, gc);
		
		gc.gridy = 7;
		gc.gridx = 0;
		JLabel versionsLabel = new JLabel(MainWindow.getLocalText("ui_mod_supported_versions"));
		versionsLabel.setForeground(MainWindow.TEXT);
		versionsLabel.setFont(new Font("Roboto", Font.PLAIN, 13));
		form.add(versionsLabel, gc);
		
		gc.gridx = 1;
		versionsPanel = new JPanel();
		versionsPanel.setLayout(new BoxLayout(versionsPanel, BoxLayout.Y_AXIS));
		versionsPanel.setOpaque(false);
		versionsPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x45475a), 1), BorderFactory.createEmptyBorder(8, 8, 8, 8)));
		
		versionsPanel.add(VersionButton());
		
		JScrollPane versionsScroll = new JScrollPane(versionsPanel);
		versionsScroll.setPreferredSize(new Dimension(0, 120));
		versionsScroll.setBackground(new Color(0x313244));
		versionsScroll.setBorder(null);
		form.add(versionsScroll, gc);
		
		return form;
	}
	
	private JButton VersionButton() {
		JButton addVersionBtn = new JButton(MainWindow.getLocalText("ui_add_version"));
		addVersionBtn.setBackground(new Color(0x313244));
		addVersionBtn.setForeground(MainWindow.ACCENT);
		addVersionBtn.setBorderPainted(false);
		addVersionBtn.setFocusPainted(false);
		addVersionBtn.setFont(new Font("Roboto", Font.PLAIN, 12));
		addVersionBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addVersionBtn.addActionListener(e -> addVersionField(""));
		
		return addVersionBtn;
	}
	
	private void addFormRow(JPanel panel, GridBagConstraints gc, String label, JComponent field, int row) {
		gc.gridy = row;
		gc.gridx = 0;
		gc.weightx = 0.2;
		JLabel lbl = new JLabel(label);
		lbl.setForeground(MainWindow.TEXT);
		lbl.setFont(new Font("Roboto", Font.PLAIN, 13));
		panel.add(lbl, gc);
		
		gc.gridx = 1;
		gc.weightx = 0.8;
		panel.add(field, gc);
	}
	
	private void refreshVersionFields() {
		
		while (versionsPanel.getComponentCount() > 1) {
			Component comp = versionsPanel.getComponent(0);
			if (comp instanceof JPanel) {
				versionsPanel.remove(comp);
			}
		}
		versionFields.clear();
		
		for (String version : currentMod.getSupportsGameVersions()) {
			addVersionField(version);
		}
		
		Component addButton = versionsPanel.getComponent(versionsPanel.getComponentCount() - 1);
		if (! (addButton instanceof JButton) || ! ((JButton) addButton).getText().equals("+ Add Version")) {
			
			if (versionsPanel.getComponentCount() == 0 || ! ((JButton) versionsPanel.getComponent(versionsPanel.getComponentCount() - 1)).getText().equals("+ Add Version")) {
				versionsPanel.add(VersionButton());
			}
		}
		
		versionsPanel.revalidate();
		versionsPanel.repaint();
	}
	
	public void refreshFieldData(ModData currentMod) {
		this.currentMod = currentMod;
		
		idField.setText(currentMod.getId());
		nameField.setText(currentMod.getName());
		descriptionArea.setText(currentMod.getDescription());
		authorField.setText(currentMod.getAuthor());
		versionField.setText(currentMod.getModVersion());
		createdOnField.setText(currentMod.getCreatedOn());
		modifiesLevelCheck.setSelected(currentMod.isModifiesLevel());
		
		refreshVersionFields();
	}
	
	private void addVersionField(String value) {
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setOpaque(false);
		
		JTextField versionField = new JTextField(value);
		versionField.setBackground(new Color(0x313244));
		versionField.setForeground(MainWindow.TEXT);
		versionField.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x45475a), 1), BorderFactory.createEmptyBorder(4, 8, 4, 8)));
		versionField.setFont(new Font("Roboto", Font.PLAIN, 12));
		
		JButton removeBtn = new JButton("✕");
		removeBtn.setBackground(new Color(0x313244));
		removeBtn.setForeground(MainWindow.DANGER);
		removeBtn.setBorderPainted(false);
		removeBtn.setFocusPainted(false);
		removeBtn.setFont(new Font("Roboto", Font.BOLD, 11));
		removeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		removeBtn.addActionListener(e -> {
			versionsPanel.remove(row);
			versionFields.remove(versionField);
			versionsPanel.revalidate();
			versionsPanel.repaint();
		});
		
		row.add(versionField, BorderLayout.CENTER);
		row.add(removeBtn, BorderLayout.EAST);
		
		int addButtonIndex = versionsPanel.getComponentCount() - 1;
		if (addButtonIndex >= 0 && versionsPanel.getComponent(addButtonIndex) instanceof JButton) {
			versionsPanel.add(row, addButtonIndex);
		} else {
			versionsPanel.add(row);
		}
		versionFields.add(versionField);
		
		versionsPanel.revalidate();
		versionsPanel.repaint();
	}
	
	private void saveManifest(String gameDir) {
		
		if (nameField.getText().isBlank()) {
			window.snackbar.show("ui_error_mod_name_required", BarManager.Type.ERROR);
			return;
		}
		if (versionField.getText().isBlank()) {
			window.snackbar.show("ui_error_mod_version_required", BarManager.Type.ERROR);
			return;
		}
		
		List<String> list = new LinkedList<>();
		for (JTextField field : versionFields) {
			String version = field.getText().trim();
			if (! version.isBlank())
				list.add(version);
		}
		
		currentMod.setId(idField.getText());
		currentMod.setName(nameField.getText());
		currentMod.setDescription(descriptionArea.getText());
		currentMod.setAuthor(authorField.getText());
		currentMod.setModVersion(versionField.getText());
		currentMod.setCreatedOn(createdOnField.getText());
		currentMod.setModifiesLevel(modifiesLevelCheck.isSelected());
		currentMod.setSupportsGameVersions(list);
		
		boolean success = ModService.writeModAsXml(gameDir, currentMod);
		ConfigService.saveModConfig(Util.modFolder(gameDir, currentMod.getId()), currentMod);
		
		if (success) {
			window.snackbar.show("ui_manifest_saved", BarManager.Type.SUCCESS, currentMod.getName());
		} else {
			window.snackbar.show("ui_manifest_save_failed", BarManager.Type.ERROR);
		}
	}
	
	private void exportMod() {
		// Get data needed for background thread (copy to avoid threading issues)
		var gameDir = window.getRegistry().userConfig.getGameDir();
		var mod = currentMod;
		// SwingUtilities.invokeLater(()->exportButton.setEnabled(false));
		// Run heavy work off EDT
		executor.submit(() -> {
			try {
				saveManifest(gameDir);  // Heavy?
				ModService.exportMod(mod, gameDir);  // Heavy!
				
				// UI updates back on EDT
				SwingUtilities.invokeLater(() -> window.snackbar.show("ui_mod_exported", BarManager.Type.SUCCESS, mod.getId() + ".pak"));
			} catch (Exception e) {
				SwingUtilities.invokeLater(() -> window.snackbar.show("ui_export_failed", BarManager.Type.ERROR));
				log.warn("error while exporting", Util.limitStackTrace(e, 10));
			}
		});
	}

	private void openFolder() {
		var gameDir = window.getRegistry().userConfig.getGameDir();
		var modPath = Util.modFolder(gameDir, currentMod.getId());
		Util.openDirectory(this, modPath);
	}
	
	private void deleteMod() {
		int confirm = JOptionPane.showConfirmDialog(this, MainWindow.getLocalText("ui_delete_mod_confirm", currentMod.getName()), MainWindow.getLocalText("ui_confirm_delete"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (confirm == JOptionPane.YES_OPTION) {
			var gameDir = window.getRegistry().userConfig.getGameDir();
			var modPath = Path.of(Util.modFolder(gameDir, currentMod.getId()));
			
			try {
				if (Files.exists(modPath)) {
					IOUtil.deleteRecursively(modPath);
					window.snackbar.show("ui_mod_deleted", BarManager.Type.SUCCESS, currentMod.getName());
				}
				
				ModService.modCollection.remove(currentMod);
				window.navigate(MainWindow.Page.MODS);
			} catch (Exception e) {
				window.snackbar.show("ui_delete_failed", BarManager.Type.ERROR, e.getMessage());
			}
		}
	}
}