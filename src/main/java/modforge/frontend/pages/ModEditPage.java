package modforge.frontend.pages;

import modforge.backend.*;
import modforge.backend.service.*;
import modforge.frontend.*;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.logging.*;

// =============================================================================
//  MOD EDIT PAGE  (create / edit a mod with full manifest editing)
// =============================================================================
public class ModEditPage extends BasePage {

	private ModData currentMod;

	private JTextField idField;
	private JTextField nameField;
	private JTextArea descriptionArea;
	private JTextField authorField;
	private JTextField versionField;
	private JTextField createdOnField;
	private JCheckBox modifiesLevelCheck;
	private JPanel versionsPanel;
	private final List<JTextField> versionFields = new ArrayList<>();

	public ModEditPage(MainWindow w) {
		super(w);
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

		setLayout(new BorderLayout(0, 16));
		add(header("Mod Editor"), BorderLayout.NORTH);

		// Main content with scroll
		JScrollPane scrollPane = new JScrollPane(buildForm());
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.getViewport().setBackground(MainWindow.BG);
		scrollPane.setBackground(MainWindow.BG);
		add(scrollPane, BorderLayout.CENTER);

		// Bottom buttons
		JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
		bottomBar.setOpaque(false);
		bottomBar.add(primaryBtn("Save Manifest", e -> saveManifest()));
		bottomBar.add(primaryBtn("Export to PAK", e -> exportMod()));
		bottomBar.add(primaryBtn("Delete Mod", e -> deleteMod()));
		bottomBar.add(primaryBtn("Back", e -> window.navigate(MainWindow.Page.MODS)));
		add(bottomBar, BorderLayout.SOUTH);
	}

	private JPanel buildForm() {
		JPanel form = new JPanel();
		form.setLayout(new GridBagLayout());
		form.setOpaque(false);
		form.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

		GridBagConstraints gc = new GridBagConstraints();
		gc.insets = new Insets(8, 12, 8, 12);
		gc.fill = GridBagConstraints.HORIZONTAL;

		addFormRow(form, gc, "Mod ID", idField = styledField(""), 0);
		addFormRow(form, gc, "Name *", nameField = styledField(""), 1);
		addFormRow(form, gc, "Author", authorField = styledField(""), 2);
		addFormRow(form, gc, "Version *", versionField = styledField(""), 3);
		addFormRow(form, gc, "Created On", createdOnField = styledField(""), 4);

		// Description
		gc.gridy = 5;
		gc.gridx = 0;
		gc.weightx = 0.2;
		JLabel descLabel = new JLabel("Description");
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
		descriptionArea.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(0x45475a), 1),
				BorderFactory.createEmptyBorder(6, 10, 6, 10)
		));
		descriptionArea.setFont(new Font("Roboto", Font.PLAIN, 13));
		descriptionArea.setLineWrap(true);
		descriptionArea.setWrapStyleWord(true);

		JScrollPane descScroll = new JScrollPane(descriptionArea);
		descScroll.setBackground(new Color(0x313244));
		descScroll.setBorder(null);
		form.add(descScroll, gc);

		// Modifies Level
		gc.gridy = 6;
		gc.gridx = 0;
		gc.weightx = 0.2;
		JLabel levelLabel = new JLabel("Modifies Level");
		levelLabel.setForeground(MainWindow.TEXT);
		levelLabel.setFont(new Font("Roboto", Font.PLAIN, 13));
		form.add(levelLabel, gc);

		gc.gridx = 1;
		modifiesLevelCheck = new JCheckBox();
		modifiesLevelCheck.setSelected(false);
		modifiesLevelCheck.setBackground(new Color(0x313244));
		modifiesLevelCheck.setForeground(MainWindow.TEXT);
		form.add(modifiesLevelCheck, gc);

		// Supported Game Versions
		gc.gridy = 7;
		gc.gridx = 0;
		JLabel versionsLabel = new JLabel("Supported Versions");
		versionsLabel.setForeground(MainWindow.TEXT);
		versionsLabel.setFont(new Font("Roboto", Font.PLAIN, 13));
		form.add(versionsLabel, gc);

		gc.gridx = 1;
		versionsPanel = new JPanel();
		versionsPanel.setLayout(new BoxLayout(versionsPanel, BoxLayout.Y_AXIS));
		versionsPanel.setOpaque(false);
		versionsPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(0x45475a), 1),
				BorderFactory.createEmptyBorder(8, 8, 8, 8)
		));

		// Add the "Add Version" button as the last component
		versionsPanel.add(VersionButton());

		JScrollPane versionsScroll = new JScrollPane(versionsPanel);
		versionsScroll.setPreferredSize(new Dimension(0, 120));
		versionsScroll.setBackground(new Color(0x313244));
		versionsScroll.setBorder(null);
		form.add(versionsScroll, gc);

		return form;
	}

	private JButton VersionButton() {
		JButton addVersionBtn = new JButton("+ Add Version");
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
		// Clear all version fields except the add button
		while (versionsPanel.getComponentCount() > 1) {
			Component comp = versionsPanel.getComponent(0);
			if (comp instanceof JPanel) {
				versionsPanel.remove(comp);
			}
		}
		versionFields.clear();

		// Add version fields for each supported version
		for (String version : currentMod.supportsGameVersions) {
			addVersionField(version);
		}

		// Ensure the add button is at the end
		Component addButton = versionsPanel.getComponent(versionsPanel.getComponentCount() - 1);
		if (!(addButton instanceof JButton) || !((JButton) addButton).getText().equals("+ Add Version")) {
			// If the last component isn't the add button, add it
			if (versionsPanel.getComponentCount() == 0 ||
					!((JButton) versionsPanel.getComponent(versionsPanel.getComponentCount() - 1)).getText().equals("+ Add Version")) {
				versionsPanel.add(VersionButton());
			}
		}

		versionsPanel.revalidate();
		versionsPanel.repaint();
	}

	public void refreshFieldData(ModData currentMod) {
		this.currentMod = currentMod;
		// Update text fields
		idField.setText(currentMod.id);
		nameField.setText(currentMod.name != null ? currentMod.name : "");
		descriptionArea.setText(currentMod.description != null ? currentMod.description : "");
		authorField.setText(currentMod.author != null ? currentMod.author : "");
		versionField.setText(currentMod.modVersion != null ? currentMod.modVersion : "");
		createdOnField.setText(currentMod.createdOn != null ? currentMod.createdOn : "");
		modifiesLevelCheck.setSelected(currentMod.modifiesLevel);

		// Refresh the versions panel with current supported versions
		refreshVersionFields();
	}

	private void addVersionField(String value) {
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setOpaque(false);

		JTextField versionField = new JTextField(value);
		versionField.setBackground(new Color(0x313244));
		versionField.setForeground(MainWindow.TEXT);
		versionField.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(0x45475a), 1),
				BorderFactory.createEmptyBorder(4, 8, 4, 8)
		));
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

		// Insert before the add button (which should be the last component)
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

	private void saveManifest() {
		// Validate required fields
		if (nameField.getText().isBlank()) {
			window.snackbar.show("Mod name is required", BarManager.Type.ERROR);
			return;
		}
		if (versionField.getText().isBlank()) {
			window.snackbar.show("Mod version is required", BarManager.Type.ERROR);
			return;
		}

		// Update mod object
		currentMod.id = idField.getText();
		currentMod.name = nameField.getText();
		currentMod.description = descriptionArea.getText();
		currentMod.author = authorField.getText();
		currentMod.modVersion = versionField.getText();
		currentMod.createdOn = createdOnField.getText();
		currentMod.modifiesLevel = modifiesLevelCheck.isSelected();

		// Update supported versions
		currentMod.supportsGameVersions.clear();
		for (JTextField field : versionFields) {
			String version = field.getText().trim();
			if (!version.isBlank()) {
				currentMod.supportsGameVersions.add(version);
			}
		}

		// Write manifest
		final String gameDir = window.getRegistry().userConfig.gameDirectory;
		boolean success = ModService.writeModAsXml(gameDir, currentMod);
		ConfigService.saveModConfigFromMod(gameDir, currentMod);

		if (success) {
			window.snackbar.show("Manifest saved for " + currentMod.name, BarManager.Type.SUCCESS);
		} else {
			window.snackbar.show("Failed to save manifest", BarManager.Type.ERROR);
		}
	}

	private void exportMod() {
		try {
			saveManifest();
			window.getRegistry().modService.exportMod(currentMod);
			window.snackbar.show("Mod exported to PAK: " + currentMod.id + ".pak", BarManager.Type.SUCCESS);
		} catch (final Exception ex) {
			window.snackbar.show("Failed to export mod", BarManager.Type.ERROR);
			Logger log = Logger.getLogger(ModEditPage.class.getName());
			log.warning("error while exporting: "+ ex);
		}
	}

	private void deleteMod() {
		int confirm = JOptionPane.showConfirmDialog(
				this,
				"Are you sure you want to delete mod '" + currentMod.name + "'?\nThis will remove the mod folder from your game directory.",
				"Confirm Delete",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE
		);

		if (confirm == JOptionPane.YES_OPTION) {
			String gameDir = window.getRegistry().userConfig.gameDirectory;
			java.nio.file.Path modPath = java.nio.file.Path.of(gameDir, "Mods", currentMod.id);

			try {
				if (java.nio.file.Files.exists(modPath)) {
					deleteRecursively(modPath.toFile());
					window.snackbar.show("Mod deleted: " + currentMod.name, BarManager.Type.SUCCESS);
				}

				window.getRegistry().modService.modCollection.remove(currentMod);
				window.navigate(MainWindow.Page.MODS);
			} catch (Exception e) {
				window.snackbar.show("Failed to delete mod: " + e.getMessage(), BarManager.Type.ERROR);
			}
		}
	}

	private void deleteRecursively(java.io.File file) {
		if (file.isDirectory()) {
			java.io.File[] children = file.listFiles();
			if (children != null) {
				for (java.io.File child : children) {
					deleteRecursively(child);
				}
			}
		}
		file.delete();
	}
}