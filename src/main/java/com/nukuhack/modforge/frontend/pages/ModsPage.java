package com.nukuhack.modforge.frontend.pages;

import com.nukuhack.modforge.Singleton;
import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.backend.ModData;
import com.nukuhack.modforge.backend.service.ModService;
import com.nukuhack.modforge.frontend.BarManager;
import com.nukuhack.modforge.frontend.MainWindow;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.Set;

@lombok.extern.slf4j.Slf4j
public class ModsPage extends BasePage {
	
	private final JList<ModData> modList;
	private final DefaultListModel<ModData> listModel;
	
	public ModsPage(MainWindow w) {
		super(w);
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
		
		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		top.add(header("ui_mods_loaded"), BorderLayout.WEST);
		
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		actions.setOpaque(false);
		actions.add(primaryBtn("ui_mod_new", e -> createNewMod()));
		actions.add(primaryBtn("ui_refresh", e -> refreshMods()));
		actions.add(primaryBtn("ui_mod_import", e -> importMod()));
		top.add(actions, BorderLayout.EAST);
		
		listModel = new DefaultListModel<>();
		modList = new JList<>(listModel);
		modList.setCellRenderer(new ModListCellRenderer());
		modList.setBackground(MainWindow.SURFACE);
		modList.setForeground(MainWindow.TEXT);
		modList.setFont(new Font("Roboto", Font.PLAIN, 13));
		modList.setSelectionBackground(new Color(0x313244));
		modList.setFixedCellHeight(60);
		modList.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
		
		modList.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					final ModData selected = modList.getSelectedValue();
					if (selected != null) {
						window.navigate(MainWindow.Page.MOD_EDIT, selected);
					}
				}
			}
		});
		
		var scroll = new JScrollPane(modList);
		scroll.setBackground(MainWindow.SURFACE);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		
		JPanel card = card("ui_mods_loaded");
		card.add(scroll, BorderLayout.CENTER);
		
		add(top, BorderLayout.NORTH);
		add(card, BorderLayout.CENTER);
	}
	
	@Override
	public void refresh(Object... input) {
		this.refreshMods();
	}
	
	public void refreshMods() {
		listModel.clear();
		
		for (var mod : ModService.modCollection) {
			listModel.addElement(mod);
		}
		
		window.snackbar.show("ui_mods_loaded_count", BarManager.Type.SUCCESS, listModel.size());
	}
	
	private void createNewMod() {
		String modId = "new_mod_" + Util.randomString(32);

		if (ModService.modCollection.stream().anyMatch(mod -> mod.getId().equals(modId))) {
			log.warn("createNewMod: mod ID '{}' already exists.", modId);
			window.navigate(MainWindow.Page.MOD_EDIT, new ModData());
			return;
		}
		var m = new ModData(
				modId, "New Mod", "Your mod description",
				window.getRegistry().userConfig.getUserName(),
				"1.0", LocalDate.now().toString(), false
		);
		ModService.modCollection.add(m);
		log.info("Mod {} created.", modId);

		m.setSupportsGameVersions(Set.of("1.0", "1.1", "1.2"));
		window.navigate(MainWindow.Page.MOD_EDIT, m);
	}
	
	private void importMod() {
		var chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setDialogTitle(MainWindow.getLocalText("ui_select_mod_folder"));
		
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
			return;
		final var modPath = chooser.getSelectedFile().toPath();
		
		var mod = ModService.loadMod(modPath);
		if (mod == null) {
			window.snackbar.show("ui_import_failed_mod_null", BarManager.Type.ERROR);
			return;
		}
		if (ModService.modCollection.contains(mod)) {
			window.snackbar.show("ui_mod_already_exists", BarManager.Type.WARNING, mod.getId());
			return;
		}
		
		ModService.modCollection.add(mod);
		
		refreshMods();
		window.snackbar.show("ui_mod_imported", BarManager.Type.SUCCESS, mod.getName());
	}
	
	private static class ModListCellRenderer extends JPanel implements ListCellRenderer<ModData> {
		private final JLabel nameLabel = new JLabel();
		private final JLabel versionLabel = new JLabel();
		private final JLabel authorLabel = new JLabel();
		private final JLabel statusLabel = new JLabel();
		
		ModListCellRenderer() {
			setLayout(new BorderLayout(12, 0));
			setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
			setBackground(MainWindow.SURFACE);
			
			JPanel leftPanel = new JPanel(new BorderLayout(8, 4));
			leftPanel.setOpaque(false);
			
			nameLabel.setFont(new Font("Roboto", Font.BOLD, 14));
			nameLabel.setForeground(MainWindow.TEXT);
			
			JPanel metaPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
			metaPanel.setOpaque(false);
			versionLabel.setFont(new Font("Roboto", Font.PLAIN, 11));
			versionLabel.setForeground(MainWindow.MUTED);
			authorLabel.setFont(new Font("Roboto", Font.PLAIN, 11));
			authorLabel.setForeground(MainWindow.MUTED);
			metaPanel.add(versionLabel);
			metaPanel.add(new JLabel("•"));
			metaPanel.add(authorLabel);
			
			leftPanel.add(nameLabel, BorderLayout.NORTH);
			leftPanel.add(metaPanel, BorderLayout.SOUTH);
			
			add(leftPanel, BorderLayout.CENTER);
			
			statusLabel.setFont(new Font("Roboto", Font.PLAIN, 11));
			add(statusLabel, BorderLayout.EAST);
		}
		
		@Override
		public Component getListCellRendererComponent(JList<? extends ModData> list, ModData mod, int index, boolean isSelected, boolean cellHasFocus) {
			var id = mod.getId();
			var name = mod.getName();
			var auth = mod.getAuthor();
			nameLabel.setText(! name.isBlank() ? name : id);
			versionLabel.setText("v" + mod.getModVersion());
			authorLabel.setText(! auth.isBlank() ? auth: "ui_unknown");
			
			boolean isExternal = auth.isBlank() || ! auth.equals(Singleton.getRegistry().userConfig.getUserName());
			if (isExternal) {
				statusLabel.setText(MainWindow.getLocalText("ui_external"));
				statusLabel.setForeground(Color.YELLOW);
			} else {
				statusLabel.setText(MainWindow.getLocalText("ui_editable"));
				statusLabel.setForeground(Color.GREEN);
			}
			
			if (isSelected) {
				setBackground(new Color(0x313244));
			} else {
				setBackground(MainWindow.SURFACE);
			}
			
			return this;
		}
	}
}