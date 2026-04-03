package modforge.frontend.pages;

import modforge.Singleton;
import modforge.Util;
import modforge.backend.service.UserService;
import modforge.frontend.BarManager;
import modforge.frontend.MainWindow;

import javax.swing.*;
import java.awt.*;

// =============================================================================
//  SETTINGS PAGE
// =============================================================================
public class SettingsPage extends BasePage {

	@Override
	public void refresh(Object... input) {
		loadSettings();
	}

	private final JTextField gameDir = styledField("e.g. C:/SteamLibrary/…/KingdomComeDeliverance2");
	private final JPanel card = card(null);
	private final JTextField userName = styledField("Your name (used as mod author)");
	private final JComboBox<String> langBox = new JComboBox<>(
new String[]{"en – English", "de – Deutsch", "fr – Français", "es – Español", "it – Italiano", "pl – Polski", "ru – Русский", "cs – Čeština"});
	private final UserService configService;

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
		card.add(primaryBtn("Browse…", e -> {
			Util.pickFolderAsync().thenAccept(path -> {
				if (path != null) SwingUtilities.invokeLater(() -> {
					gameDir.setText(path);
					configService.gameDirectory = path;
					configService.save();
					w.snackbar.show("Game directory set", BarManager.Type.SUCCESS);
				});
			});
		}), gc);

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

		// Save button (gridy = 4)
		gc.gridx = 1;
		gc.gridy = 4;
		gc.weightx = 0;
		gc.gridwidth = 1;
		card.add(primaryBtn("Save Settings", e -> {
			configService.userName = userName.getText();
			configService.language = langCode();
			Singleton.INSTANCE.getRegistry().init();
			w.snackbar.show("Settings saved", BarManager.Type.SUCCESS);
		}), gc);

		return card;
	}

	private void loadSettings() {
		final var config = configService;

		// Load game directory if exists
		if (config.gameDirectory != null && !config.gameDirectory.isEmpty()) {
			gameDir.setText(config.gameDirectory);
			gameDir.setForeground(MainWindow.TEXT);
		}

		// Load username if exists
		if (config.userName != null && !config.userName.isEmpty()) {
			userName.setText(config.userName);
		}

		// Load language if exists
		if (config.language != null && !config.language.isEmpty()) {
			String targetLangCode = config.language;
			for (int i = 0; i < langBox.getItemCount(); i++) {
				String item = langBox.getItemAt(i);
				if (item != null && item.startsWith(targetLangCode)) {
					langBox.setSelectedIndex(i);
					break;
				}
			}
		}
	}

	private static JLabel label(String text) {
		JLabel l = new JLabel(text);
		l.setForeground(MainWindow.TEXT);
		l.setFont(new Font("Roboto", Font.PLAIN, 13));
		return l;
	}

	private String langCode() {
		String sel = (String) langBox.getSelectedItem();
		return sel != null ? sel.substring(0, 2) : "en";
	}
}
