package com.nukuhack.modforge.frontend.pages;

import com.nukuhack.modforge.Singleton;
import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.backend.model.E.Language;
import com.nukuhack.modforge.backend.service.UserConfig;
import com.nukuhack.modforge.frontend.BarManager;
import com.nukuhack.modforge.frontend.MainWindow;

import javax.swing.*;
import java.awt.*;

// =============================================================================
//  SETTINGS PAGE
// =============================================================================
@lombok.extern.slf4j.Slf4j
public class SettingsPage extends BasePage {
	
	private final JTextField gameDir = styledField("e.g. C:/SteamLibrary/…/KingdomComeDeliverance2");
	private final JPanel card = card(null);
	private final JTextField userName = styledField("Your name (used as mod author)");
	private final JComboBox<String> langBox = new JComboBox<>(Language.getAllLang());
	private final JCheckBox loadGameData = new JCheckBox();
	private final UserConfig configService;
	
	public SettingsPage(MainWindow w) {
		super(w);
		configService = w.getRegistry().userConfig;
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
		add(header("Settings"), BorderLayout.NORTH);
		
		card.setLayout(new GridBagLayout());
		
		// Create UI components
		JPanel cardWithComponents = createSettingsCard(w);
		add(cardWithComponents, BorderLayout.CENTER);
	}
	
	private static JLabel label(String text) {
		JLabel l = new JLabel(text);
		l.setForeground(MainWindow.TEXT);
		l.setFont(new Font("Roboto", Font.PLAIN, 13));
		return l;
	}
	
	@Override
	public void refresh(Object... input) {
		loadSettings();
	}
	
	private JPanel createSettingsCard(MainWindow w) {
		GridBagConstraints gc = new GridBagConstraints();
		gc.insets = new Insets(8, 6, 8, 6);
		gc.fill = GridBagConstraints.HORIZONTAL;
		
		// Reset gridwidth and increment gridy for the next row
		gc.gridwidth = 1;
		gc.gridy = 1;
		
		// Game directory row
		gc.gridx = 0;
		gc.weightx = 0.15;
		card.add(label("Game Directory"), gc);
		gc.gridx = 1;
		gc.weightx = 0.75;
		gameDir.setForeground(MainWindow.TEXT);
		card.add(gameDir, gc);
		gc.gridx = 2;
		gc.weightx = 0.1;
		card.add(primaryBtn("Browse…", e -> Util.pickFolderAsync().thenAccept(path -> {
			if (path != null) {
				gameDir.setText(path);
				gameDir.setForeground(MainWindow.TEXT);
				w.snackbar.show("Game directory set", BarManager.Type.SUCCESS);
			}
		})), gc);
		
		// Username row (gridy = 2)
		gc.gridx = 0;
		gc.gridy = 2;
		gc.weightx = 0.15;
		card.add(label("Author Name"), gc);
		gc.gridx = 1;
		gc.weightx = 0.85;
		gc.gridwidth = 2;
		userName.setForeground(MainWindow.TEXT);
		card.add(userName, gc);
		
		// Language row (gridy = 3)
		gc.gridx = 0;
		gc.gridy = 3;
		gc.weightx = 0.15;
		gc.gridwidth = 1;
		card.add(label("Language"), gc);
		gc.gridx = 1;
		gc.weightx = 0.85;
		gc.gridwidth = 2;
		langBox.setBackground(new Color(0x313244));
		langBox.setForeground(MainWindow.TEXT);
		langBox.setFont(new Font("Roboto", Font.PLAIN, 13));
		card.add(langBox, gc);
		
		// Username row (gridy = 4)
		gc.gridx = 0;
		gc.gridy = 4;
		gc.gridwidth = 1;
		gc.weightx = 0.15;
		card.add(label("Load game data at Startup"), gc);
		gc.gridx = 1;
		gc.gridwidth = 1;
		card.add(loadGameData, gc);
		
		// Save button (gridy = 5)
		gc.gridx = 1;
		gc.gridy = 5;
		gc.weightx = 0;
		gc.gridwidth = 1;
		card.add(primaryBtn("Save Settings", e -> {
			configService.setGameDirectory(gameDir.getText());
			configService.setUserName(userName.getText());
			final var sel = (Language) langBox.getSelectedItem();
			if (sel != null)
				configService.setLanguage(sel);
			configService.setAutoLoadGameData(loadGameData.isSelected());
			
			w.snackbar.show("Settings saved", BarManager.Type.SUCCESS);
		}), gc);
		gc.gridx = 2;
		gc.gridy = 5;
		gc.weightx = 0;
		gc.gridwidth = 1;
		card.add(primaryBtn("Refresh game&mod data", e -> {
			Singleton.INSTANCE.getRegistry().init();
			w.snackbar.show("Data refreshed", BarManager.Type.SUCCESS);
		}), gc);
		
		return card;
	}
	
	private void loadSettings() {
		// Load game directory if exists
		var gameDire = configService.getGameDirectory();
		if (! gameDire.isEmpty()) {
			gameDir.setText(gameDire);
			gameDir.setForeground(MainWindow.TEXT);
		}
		
		// Load username if exists
		var userNam = configService.getUserName();
		if (! userNam.isEmpty())
			userName.setText(userNam);
		
		// Load language if exists
		var lang = configService.getLanguage().getDisplayName();
		for (int i = 0; i < langBox.getItemCount(); i++) {
			final var item = langBox.getItemAt(i);
			if (lang.equals(item)) {
				langBox.setSelectedIndex(i);
				break;
			}
		}
		
		boolean loadData = configService.isAutoLoadGameData();
		loadGameData.setSelected(loadData);
	}
}
