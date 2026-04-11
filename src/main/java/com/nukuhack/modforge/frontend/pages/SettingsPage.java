package com.nukuhack.modforge.frontend.pages;

import com.nukuhack.modforge.Singleton;
import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.backend.model.E.Language;
import com.nukuhack.modforge.backend.service.UserConfig;
import com.nukuhack.modforge.frontend.BarManager;
import com.nukuhack.modforge.frontend.MainWindow;

import javax.swing.*;
import java.awt.*;

import static com.nukuhack.modforge.frontend.MainWindow.getLocalText;

@lombok.extern.slf4j.Slf4j
public class SettingsPage extends BasePage {
	
	private final JTextField gameDir = styledField("e.g. C:/SteamLibrary/…/KingdomComeDeliverance2");
	private final JPanel card = card(null);
	private final JTextField userName = styledField("ui_username_placeholder");
	private final JComboBox<String> langBox = new JComboBox<>(Language.getAllLang());
	private final JCheckBox loadGameData = new JCheckBox();
	private final UserConfig userConfig;
	
	public SettingsPage(MainWindow w) {
		super(w);
		userConfig = w.getRegistry().userConfig;
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
		add(header("ui_settings"), BorderLayout.NORTH);
		
		card.setLayout(new GridBagLayout());
		
		JPanel cardWithComponents = createSettingsCard(w);
		add(cardWithComponents, BorderLayout.CENTER);
	}
	
	private static JLabel label(String text) {
		JLabel l = new JLabel(getLocalText(text));
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
		
		gc.gridwidth = 1;
		gc.gridy = 1;
		
		gc.gridx = 0;
		gc.weightx = 0.15;
		card.add(label("ui_game_directory"), gc);
		gc.gridx = 1;
		gc.weightx = 0.75;
		gameDir.setForeground(MainWindow.TEXT);
		card.add(gameDir, gc);
		gc.gridx = 2;
		gc.weightx = 0.1;
		card.add(primaryBtn("ui_browse", e -> Util.pickFolderAsync().thenAccept(path -> {
			if (path != null) {
				gameDir.setText(path);
				gameDir.setForeground(MainWindow.TEXT);
				w.snackbar.show("Game directory set", BarManager.Type.SUCCESS);
			}
		})), gc);
		
		gc.gridx = 0;
		gc.gridy = 2;
		gc.weightx = 0.15;
		card.add(label("ui_author_name"), gc);
		gc.gridx = 1;
		gc.weightx = 0.85;
		gc.gridwidth = 2;
		userName.setForeground(MainWindow.TEXT);
		card.add(userName, gc);
		
		gc.gridx = 0;
		gc.gridy = 3;
		gc.weightx = 0.15;
		gc.gridwidth = 1;
		card.add(label("ui_language"), gc);
		gc.gridx = 1;
		gc.weightx = 0.85;
		gc.gridwidth = 2;
		langBox.setBackground(new Color(0x313244));
		langBox.setForeground(MainWindow.TEXT);
		langBox.setFont(new Font("Roboto", Font.PLAIN, 13));
		card.add(langBox, gc);
		
		gc.gridx = 0;
		gc.gridy = 4;
		gc.gridwidth = 1;
		gc.weightx = 0.15;
		card.add(label("ui_load_game_data_startup"), gc);
		gc.gridx = 1;
		gc.gridwidth = 1;
		card.add(loadGameData, gc);
		
		gc.gridx = 1;
		gc.gridy = 5;
		gc.weightx = 0;
		gc.gridwidth = 1;
		card.add(primaryBtn("ui_settings_save", e -> {
			userConfig.setGameDirectory(gameDir.getText());
			userConfig.setUserName(userName.getText());
			final var sel = (String) langBox.getSelectedItem();
			if (sel != null)
				userConfig.setLanguage(Language.fromDisplayName(sel));
			userConfig.setAutoLoadGameData(loadGameData.isSelected());
			executor.submit(() -> {
				userConfig.save();
				SwingUtilities.invokeLater(() -> w.snackbar.show("ui_settings_saved", BarManager.Type.SUCCESS));
			});
		}), gc);
		gc.gridx = 2;
		gc.gridy = 5;
		gc.weightx = 0;
		gc.gridwidth = 1;
		card.add(primaryBtn("ui_refresh_all", e -> executor.submit(() -> {
			Singleton.INSTANCE.getRegistry().init();
			SwingUtilities.invokeLater(() -> w.snackbar.show("ui_refresh_success", BarManager.Type.SUCCESS));
		})), gc);
		
		return card;
	}
	
	private void loadSettings() {
		
		var gameDire = userConfig.getGameDirectory();
		if (! gameDire.isEmpty()) {
			gameDir.setText(gameDire);
			gameDir.setForeground(MainWindow.TEXT);
		}
		
		var userNam = userConfig.getUserName();
		if (! userNam.isEmpty())
			userName.setText(userNam);
		
		var lang = userConfig.getLanguage().getDisplayName();
		for (int i = 0; i < langBox.getItemCount(); i++) {
			final var item = langBox.getItemAt(i);
			if (lang.equals(item)) {
				langBox.setSelectedIndex(i);
				break;
			}
		}
		
		boolean loadData = userConfig.isAutoLoadGameData();
		loadGameData.setSelected(loadData);
	}
}

