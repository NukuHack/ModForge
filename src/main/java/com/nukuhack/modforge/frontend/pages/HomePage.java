package com.nukuhack.modforge.frontend.pages;

import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.frontend.MainWindow;

import javax.swing.*;
import java.awt.*;

import static com.nukuhack.modforge.frontend.MainWindow.getLocalText;

@lombok.extern.slf4j.Slf4j
public class HomePage extends BasePage {
	
	public HomePage(MainWindow w) {
		super(w);
		final String gameDir = w.getRegistry().userConfig.getGameDir();
		
		setLayout(new GridBagLayout());
		setOpaque(false);
		
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 1.0;
		gbc.weighty = 1.0;
		gbc.anchor = GridBagConstraints.CENTER;
		
		JPanel card = card(null);
		card.setPreferredSize(new Dimension(480, 320));
		
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
		
		JLabel logo = new JLabel("ModForge", SwingConstants.CENTER);
		logo.setForeground(MainWindow.ACCENT);
		logo.setFont(new Font("Roboto", Font.BOLD, 32));
		logo.setAlignmentX(Component.CENTER_ALIGNMENT);
		
		JLabel sub = new JLabel(getLocalText("ui_app_subtitle"), SwingConstants.CENTER);
		sub.setForeground(MainWindow.MUTED);
		sub.setFont(new Font("Roboto", Font.PLAIN, 14));
		sub.setAlignmentX(Component.CENTER_ALIGNMENT);
		
		var but = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
		but.setOpaque(false);
		but.add(primaryBtn("ui_open_mods", e -> w.navigate(MainWindow.Page.MODS)));
		but.add(primaryBtn("ui_open_items", e -> w.navigate(MainWindow.Page.ITEMS)));
		but.add(primaryBtn("ui_settings", e -> w.navigate(MainWindow.Page.SETTINGS)));
		but.setAlignmentX(Component.CENTER_ALIGNMENT);
		
		JButton openGameDirBtn = primaryBtn("ui_open_game_dir", e -> Util.openDirectory(this, gameDir));
		openGameDirBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		
		contentPanel.add(logo);
		contentPanel.add(Box.createVerticalStrut(8));
		contentPanel.add(sub);
		contentPanel.add(Box.createVerticalStrut(24));
		contentPanel.add(but);
		contentPanel.add(Box.createVerticalStrut(16));
		
		contentPanel.add(openGameDirBtn);
		
		card.add(contentPanel, cardGbc);
		add(card, gbc);
	}
	
	@Override
	public void refresh(Object... input) {
	
	}
}