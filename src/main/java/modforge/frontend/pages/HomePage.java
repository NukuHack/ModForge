package modforge.frontend.pages;

import modforge.Util;
import modforge.frontend.MainWindow;

import javax.swing.*;
import java.awt.*;

// =============================================================================
//  HOME PAGE
// =============================================================================
public class HomePage extends BasePage {
	
	public HomePage(MainWindow w) {
		super(w);
		final String gameDir = w.getRegistry().userConfig.gameDirectory;
		
		// Use GridBagLayout for perfect centering
		setLayout(new GridBagLayout());
		setOpaque(false);
		
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 1.0;
		gbc.weighty = 1.0;
		gbc.anchor = GridBagConstraints.CENTER;
		
		JPanel card = card(null);
		card.setPreferredSize(new Dimension(480, 320)); // Increased height for new button
		
		// Use GridBagLayout for the card content as well
		card.setLayout(new GridBagLayout());
		GridBagConstraints cardGbc = new GridBagConstraints();
		cardGbc.gridx = 0;
		cardGbc.gridy = 0;
		cardGbc.weightx = 1.0;
		cardGbc.weighty = 1.0;
		cardGbc.anchor = GridBagConstraints.CENTER;
		
		JPanel contentPanel = new JPanel();
		contentPanel.setOpaque(false);
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		
		JLabel logo = new JLabel("⚒  ModForge", SwingConstants.CENTER);
		logo.setForeground(MainWindow.ACCENT);
		logo.setFont(new Font("Roboto", Font.BOLD, 32));
		logo.setAlignmentX(Component.CENTER_ALIGNMENT);
		
		JLabel sub = new JLabel("Kingdom Come: Deliverance II – Mod Tool", SwingConstants.CENTER);
		sub.setForeground(MainWindow.MUTED);
		sub.setFont(new Font("Roboto", Font.PLAIN, 14));
		sub.setAlignmentX(Component.CENTER_ALIGNMENT);
		
		JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
		btns.setOpaque(false);
		btns.add(primaryBtn("Open Mods", e -> w.navigate(MainWindow.Page.MODS)));
		btns.add(primaryBtn("Browse Items", e -> w.navigate(MainWindow.Page.ITEMS)));
		btns.add(primaryBtn("Settings", e -> w.navigate(MainWindow.Page.SETTINGS)));
		btns.setAlignmentX(Component.CENTER_ALIGNMENT);
		
		// New button to open game directory
		JButton openGameDirBtn = secondaryBtn("Open Game Directory", e -> Util.openDirectory(this, gameDir));
		openGameDirBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		
		// Add some spacing before the new button
		contentPanel.add(logo);
		contentPanel.add(Box.createVerticalStrut(8));
		contentPanel.add(sub);
		contentPanel.add(Box.createVerticalStrut(24));
		contentPanel.add(btns);
		contentPanel.add(Box.createVerticalStrut(16)); // Spacing between button groups
		contentPanel.add(openGameDirBtn);
		
		card.add(contentPanel, cardGbc);
		add(card, gbc);
	}
	
	@Override
	public void refresh(Object... input) {
	
	}
}