package modforge.frontend.pages;

import modforge.Singleton;
import modforge.backend.ModData;
import modforge.backend.service.ModService;
import modforge.frontend.BarManager;
import modforge.frontend.MainWindow;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

// =============================================================================
//  MODS PAGE
// =============================================================================
@lombok.extern.slf4j.Slf4j
public class ModsPage extends BasePage {
	
	private final JList<ModData> modList;
	private final DefaultListModel<ModData> listModel;
	public ModsPage(MainWindow w) {
		super(w);
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
		
		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		top.add(header("My Mods"), BorderLayout.WEST);
		
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		actions.setOpaque(false);
		actions.add(primaryBtn("+ New Mod", e -> createNewMod()));
		actions.add(primaryBtn("Refresh", e -> refreshMods()));
		actions.add(primaryBtn("Import Mod", e -> importMod()));
		top.add(actions, BorderLayout.EAST);
		
		// Mod list with custom renderer
		listModel = new DefaultListModel<>();
		modList = new JList<>(listModel);
		modList.setCellRenderer(new ModListCellRenderer());
		modList.setBackground(MainWindow.SURFACE);
		modList.setForeground(MainWindow.TEXT);
		modList.setFont(new Font("Roboto", Font.PLAIN, 13));
		modList.setSelectionBackground(new Color(0x313244));
		modList.setFixedCellHeight(60);
		modList.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
		
		// Double-click to edit
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
		
		JScrollPane scroll = new JScrollPane(modList);
		scroll.setBackground(MainWindow.SURFACE);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		
		JPanel card = card("Installed Mods");
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
		// Add user-created mods
		for (var mod : ModService.modCollection) {
			listModel.addElement(mod);
		}
		
		window.snackbar.show("Loaded " + listModel.size() + " mods", BarManager.Type.SUCCESS);
	}
	
	private void createNewMod() {
		// Create a new mod with default values
		String timestamp = String.valueOf(System.currentTimeMillis());
		String defaultId = "new_mod_" + timestamp.substring(timestamp.length() - 6);
		
		final ModData newMod = window.getRegistry().modService.createNewMod("New Mod", "Your mod description", window.getRegistry().userConfig.userName, "1.0", java.time.LocalDate.now().toString(), defaultId, false, java.util.List.of("1.0", "1.1", "1.2"));
		
		window.navigate(MainWindow.Page.MOD_EDIT, newMod);
	}
	
	private void importMod() {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setDialogTitle("Select mod folder to import");
		
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			java.nio.file.Path modPath = chooser.getSelectedFile().toPath();
			try {
				// Try to read manifest and import
				var docBuilder = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder();
				java.nio.file.Path manifestPath = modPath.resolve("mod.manifest");
				
				if (java.nio.file.Files.exists(manifestPath)) {
					var doc = docBuilder.parse(manifestPath.toFile());
					var mod = ModService.parseModDescription(doc);
					
					// Check if already exists
					if (! ModService.modCollection.contains(mod)) {
						ModService.modCollection.add(mod);
						refreshMods();
						window.snackbar.show("Imported mod: " + mod.name, BarManager.Type.SUCCESS);
					} else {
						window.snackbar.show("Mod already exists: " + mod.id, BarManager.Type.WARNING);
					}
				} else {
					window.snackbar.show("No mod.manifest found in selected folder", BarManager.Type.ERROR);
				}
			} catch (Exception e) {
				window.snackbar.show("Import failed: " + e.getMessage(), BarManager.Type.ERROR);
				log.error("Import error: " + e);
			}
		}
	}
	
	// Custom cell renderer for mod list items
	private static class ModListCellRenderer extends JPanel implements ListCellRenderer<ModData> {
		private final JLabel nameLabel = new JLabel();
		private final JLabel versionLabel = new JLabel();
		private final JLabel authorLabel = new JLabel();
		private final JLabel statusLabel = new JLabel();
		
		ModListCellRenderer() {
			setLayout(new BorderLayout(12, 0));
			setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
			setBackground(MainWindow.SURFACE);
			
			// Left panel with icon and info
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
			nameLabel.setText(mod.name != null && ! mod.name.isBlank() ? mod.name : mod.id);
			versionLabel.setText("v" + (mod.modVersion != null ? mod.modVersion : "?"));
			authorLabel.setText(mod.author != null ? mod.author : "Unknown");
			
			// Check if mod is external (read-only)
			boolean isExternal = mod.author.isBlank() || ! mod.author.equals(Singleton.INSTANCE.getRegistry().userConfig.userName);
			if (isExternal) {
				statusLabel.setText("📥 External");
				statusLabel.setForeground(Color.YELLOW);
			} else {
				statusLabel.setText("✏️ Editable");
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