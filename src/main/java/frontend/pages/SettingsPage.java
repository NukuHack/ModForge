package frontend.pages;

import frontend.*;

import javax.swing.*;
import java.awt.*;

// =============================================================================
//  SETTINGS PAGE
// =============================================================================
public class SettingsPage extends BasePage {
	private final JTextField gameDir = styledField("e.g. C:/SteamLibrary/…/KingdomComeDeliverance2");
	private final JComboBox<String> langBox = new JComboBox<>(
			new String[]{"en – English", "de – Deutsch", "fr – Français", "es – Español",
					"it – Italiano", "pl – Polski", "ru – Русский", "cs – Čeština"});

	public SettingsPage(MainWindow w) {
		super(w);
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
		add(header("Settings"), BorderLayout.NORTH);

		JPanel card = card("Application Settings");
		card.setLayout(new GridBagLayout());

		GridBagConstraints gc = new GridBagConstraints();
		gc.insets = new Insets(8, 6, 8, 6);
		gc.fill = GridBagConstraints.HORIZONTAL;

		// Game directory row
		gc.gridx = 0;
		gc.gridy = 0;
		gc.weightx = 0.15;
		card.add(label("Game Directory"), gc);
		gc.gridx = 1;
		gc.weightx = 0.75;
		card.add(gameDir, gc);
		gc.gridx = 2;
		gc.weightx = 0.1;
		card.add(primaryBtn("Browse…", e -> {
			Util.pickFolderAsync().thenAccept(path -> {
				if (path != null) SwingUtilities.invokeLater(() -> {
					gameDir.setText(path);
					gameDir.setForeground(MainWindow.TEXT);
					// configService.getCurrent().gameDirectory = path;
					// configService.save();
					w.snackbar.show("Game directory set", BarManager.Type.SUCCESS);
				});
			});
		}), gc);

		// Username row
		gc.gridx = 0;
		gc.gridy = 1;
		gc.weightx = 0.15;
		card.add(label("Author Name"), gc);
		gc.gridx = 1;
		gc.weightx = 0.85;
		gc.gridwidth = 2;
		JTextField userName = styledField("Your name (used as mod author)");
		card.add(userName, gc);

		// Language row
		gc.gridx = 0;
		gc.gridy = 2;
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

		// Save button
		gc.gridx = 1;
		gc.gridy = 3;
		gc.weightx = 0;
		gc.gridwidth = 1;
		card.add(primaryBtn("Save Settings", e -> {
			// configService.getCurrent().userName = userName.getText();
			// configService.getCurrent().language = langCode();
			// configService.save();
			w.snackbar.show("Settings saved", BarManager.Type.SUCCESS);
		}), gc);

		add(card, BorderLayout.CENTER);
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
