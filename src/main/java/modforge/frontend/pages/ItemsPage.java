package modforge.frontend.pages;

import modforge.Singleton;
import modforge.Util;
import modforge.backend.ItemType;
import modforge.backend.ModData;
import modforge.backend.model.ModItem;
import modforge.backend.model.item.Storm;
import modforge.backend.service.ModItemBuilder;
import modforge.backend.service.ModService;
import modforge.frontend.BarManager;
import modforge.frontend.MainWindow;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ItemsPage extends BasePage {
	
	// UNDERLYING DATA - the source of truth
	private final List<ModItem> underlyingItems = new ArrayList<>();
	// DISPLAY MODEL - filtered view of underlying items
	private final DefaultListModel<String> displayModel = new DefaultListModel<>();
	private final JList<String> itemList = new JList<>(displayModel);
	// Track indices in the underlying list
	private final List<Integer> displayToUnderlyingIndex = new ArrayList<>();
	private final JTextField search = styledField("Search items…");
	private final JComboBox<String> itemTypeSelector = new JComboBox<>();
	private final JComboBox<String> modSourceSelector = new JComboBox<>();
	/** The ModData whose items are currently displayed; null means Base Game. */
	private modforge.backend.ModData activeSource = null;
	// Detail panel components
	private JLabel detailLabel;
	private ModItem selectedItem;
	public ItemsPage(final MainWindow w) {
		super(w);
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
		
		// Setup the list selection listener
		setupListSelectionListener();
		
		// Build top panel with type selector and search
		JPanel top = new JPanel(new BorderLayout(12, 0));
		top.setOpaque(false);
		
		JPanel leftPanel = new JPanel(new BorderLayout(8, 0));
		leftPanel.setOpaque(false);
		leftPanel.add(header("Game Items"), BorderLayout.WEST);
		
		JPanel filterPanel = new JPanel(new BorderLayout(8, 0));
		filterPanel.setOpaque(false);
		
		// Add item type selector for filtering by specific item class
		setupItemTypeSelector();
		
		// Add mod source selector (Base Game + loaded mods)
		setupModSourceSelector();
		
		JPanel selectorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		selectorPanel.setOpaque(false);
		selectorPanel.add(modSourceSelector);
		selectorPanel.add(itemTypeSelector);
		
		filterPanel.add(selectorPanel, BorderLayout.WEST);
		filterPanel.add(search, BorderLayout.CENTER);
		
		top.add(leftPanel, BorderLayout.WEST);
		top.add(filterPanel, BorderLayout.CENTER);
		
		// Setup search filter
		search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			public void insertUpdate(DocumentEvent e) {
				refreshDisplay(false);
			}
			
			public void removeUpdate(DocumentEvent e) {
				refreshDisplay(false);
			}
			
			public void changedUpdate(DocumentEvent e) {
				refreshDisplay(false);
			}
		});
		
		// Build main content panel (list + detail)
		JPanel mainContent = buildMainContentPanel();
		
		add(top, BorderLayout.NORTH);
		add(mainContent, BorderLayout.CENTER);
	}
	
	public static void addCopyToPopupMenu(JPopupMenu popupMenu, ModItem selectedItem) {
		final JMenuItem copyAllMenuItem = new JMenuItem("Copy All Details");
		final var window = Singleton.INSTANCE.getMainWindow();
		copyAllMenuItem.addActionListener(e -> {
			if (selectedItem != null) {
				final String allDetails = selectedItem.details();
				Util.copyText(allDetails);
				window.snackbar.show("All details copied to clipboard", BarManager.Type.INFO);
			}
		});
		popupMenu.add(copyAllMenuItem);
		
		final JMenuItem copyIdMenuItem = new JMenuItem("Copy ID");
		copyIdMenuItem.addActionListener(e -> {
			if (selectedItem != null) {
				Util.copyText(selectedItem.getId());
				window.snackbar.show("ID copied to clipboard: " + selectedItem.getId(), BarManager.Type.INFO);
			}
		});
		popupMenu.add(copyIdMenuItem);
	}
	
	@Override
	public void refresh(Object... input) {
		this.refreshAllItems();
	}
	
	/**
	 * Build and populate the mod source dropdown.
	 * First entry is always "Base Game"; subsequent entries are loaded mods.
	 */
	private void setupModSourceSelector() {
		modSourceSelector.setFont(new Font("Roboto", Font.PLAIN, 12));
		modSourceSelector.setBackground(MainWindow.SURFACE);
		modSourceSelector.setForeground(MainWindow.TEXT);
		itemTypeSelector.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x2a2a3a)), BorderFactory.createEmptyBorder(4, 8, 4, 8)));
		
		modSourceSelector.addActionListener(e -> {
			int idx = modSourceSelector.getSelectedIndex();
			if (idx <= 0) {
				activeSource = null; // Base Game
			} else {
				// idx - 1 because index 0 is "Base Game"
				var mods2 = window.getRegistry().modService.modCollection;
				activeSource = (idx - 1 < mods2.size()) ? mods2.get(idx - 1) : null;
			}
			refreshUnderlyingList();
			refreshDisplay(false);
		});
	}
	
	/**
	 * Setup item type selector for filtering by specific class
	 */
	private void setupItemTypeSelector() {
		for (final String type : ItemType.getAllTypes()) {
			itemTypeSelector.addItem(type);
		}
		
		itemTypeSelector.setFont(new Font("Roboto", Font.PLAIN, 12));
		itemTypeSelector.setBackground(MainWindow.SURFACE);
		itemTypeSelector.setForeground(MainWindow.TEXT);
		itemTypeSelector.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x2a2a3a)), BorderFactory.createEmptyBorder(4, 8, 4, 8)));
		
		itemTypeSelector.addActionListener(e -> refreshDisplay(true));
	}
	
	/**
	 * Build the main content panel with list and detail view
	 */
	private JPanel buildMainContentPanel() {
		JPanel mainPanel = new JPanel(new BorderLayout());
		mainPanel.setBackground(MainWindow.BG);
		
		JScrollPane scroll = new JScrollPane(itemList);
		scroll.setBackground(MainWindow.SURFACE);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getViewport().setBackground(MainWindow.SURFACE);
		
		// Style the list
		itemList.setBackground(new Color(0x181825));
		itemList.setForeground(MainWindow.TEXT);
		itemList.setSelectionBackground(new Color(0x313244));
		itemList.setSelectionForeground(MainWindow.TEXT);
		itemList.setFont(new Font("Roboto", Font.PLAIN, 12));
		itemList.setFixedCellHeight(28);
		
		// Create detail panel
		JPanel detailPanel = new JPanel(new BorderLayout());
		detailPanel.setBackground(new Color(0x181825));
		detailPanel.setPreferredSize(new Dimension(400, 0));
		detailPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
		
		// Create detail label with HTML content
		detailLabel = new JLabel("<html><b>Select an item</b><br/><br/>" + "<span style='color:#6c6f85'>Details will appear here.</span></html>");
		detailLabel.setForeground(MainWindow.TEXT);
		detailLabel.setCursor(new Cursor(Cursor.TEXT_CURSOR));
		
		// Add mouse listener for manual copy
		detailLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				// Check if the click was on the ID (blue text)
				// We need to detect if the click coordinates are within the ID area
				// For simplicity, we'll check if the click is in the top portion of the label
				// where the ID is displayed (first 40 pixels or so)
				if (selectedItem != null && e.getY() < 40) {
					// Auto-copy ID when clicking on the blue ID text
					String id = selectedItem.getId();
					Util.copyText(id);
					window.snackbar.show("ID copied to clipboard: " + id, BarManager.Type.INFO);
				}
			}
		});
		
		// Add right-click menu for copying all text
		final JPopupMenu popupMenu = getPopupMenu();
		
		detailLabel.setComponentPopupMenu(popupMenu);
		detailPanel.add(detailLabel, BorderLayout.NORTH);
		
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scroll, detailPanel);
		split.setDividerLocation(500);
		split.setBackground(MainWindow.BG);
		split.setDividerSize(1);
		split.setBorder(BorderFactory.createEmptyBorder());
		
		mainPanel.add(split, BorderLayout.CENTER);
		return mainPanel;
	}
	
	private JPopupMenu getPopupMenu() {
		JPopupMenu popupMenu = new JPopupMenu();
		
		addCopyToPopupMenu(popupMenu, selectedItem);
		
		final JMenuItem editItemMenuItem = new JMenuItem("Edit Item");
		editItemMenuItem.addActionListener(e -> {
			if (selectedItem == null)
				return;
			if (selectedItem instanceof Storm stormItem) {
				// Storm items get their own dedicated editor
				window.navigate(MainWindow.Page.STORM, stormItem);
			} else {
				window.navigate(MainWindow.Page.ITEM_EDIT, selectedItem);
			}
		});
		popupMenu.add(editItemMenuItem);
		
		final JMenuItem editLangMenuItem = new JMenuItem("Edit Lang");
		editLangMenuItem.addActionListener(e -> {
			if (selectedItem != null)
				window.navigate(MainWindow.Page.LANG_EDIT, selectedItem);
		});
		popupMenu.add(editLangMenuItem);
		
		final JMenuItem addModMenuItem = new JMenuItem("Add to mod");
		addModMenuItem.addActionListener(e -> {
			if (selectedItem != null) {
				showAddToModDialog(selectedItem);
			}
		});
		popupMenu.add(addModMenuItem);
		return popupMenu;
	}
	
	/**
	 * Show dialog to add item to a mod
	 */
	private void showAddToModDialog(ModItem item) {
		// Create a copy for the mod
		final var copy = ModItemBuilder.deepCopy(item);
		
		JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Add to Mod", true);
		dialog.setSize(400, 300);
		dialog.setLocationRelativeTo(this);
		dialog.setLayout(new BorderLayout());
		
		JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
		mainPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
		mainPanel.setBackground(MainWindow.BG);
		
		JLabel titleLabel = new JLabel("Add item to mod:");
		titleLabel.setForeground(MainWindow.TEXT);
		titleLabel.setFont(new Font("Roboto", Font.BOLD, 14));
		mainPanel.add(titleLabel, BorderLayout.NORTH);
		
		DefaultListModel<String> modListModel = new DefaultListModel<>();
		JList<String> modJList = new JList<>(modListModel);
		modJList.setBackground(MainWindow.SURFACE);
		modJList.setForeground(MainWindow.TEXT);
		modJList.setSelectionBackground(new Color(0x313244));
		
		ModService modService = window.getRegistry().modService;
		for (ModData mod : modService.modCollection) {
			modListModel.addElement(mod.id + " | " + mod.name);
		}
		
		JScrollPane scrollPane = new JScrollPane(modJList);
		scrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(0x313244)), "Select Mod", TitledBorder.LEFT, TitledBorder.TOP, new Font("Roboto", Font.PLAIN, 11), MainWindow.MUTED));
		mainPanel.add(scrollPane, BorderLayout.CENTER);
		
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttonPanel.setOpaque(false);
		
		JButton addBtn = new JButton("Add");
		addBtn.setBackground(MainWindow.ACCENT);
		addBtn.setForeground(new Color(0x1e1e2e));
		addBtn.addActionListener(e -> {
			String selected = modJList.getSelectedValue();
			if (selected != null) {
				String modId = selected.split(" \\| ")[0];
				modService.modCollection.stream().filter(m -> m.id.equals(modId)).findFirst().ifPresent(mod -> {
					copy.setPath(modId + ".pak:" + copy.getClass().getSimpleName().toLowerCase() + "s/" + copy.getId() + ".xml");
					mod.addItem(copy);
					window.snackbar.show("Added to mod: " + mod.name, BarManager.Type.SUCCESS);
					dialog.dispose();
				});
			}
		});
		
		JButton cancelBtn = new JButton("Cancel");
		cancelBtn.addActionListener(e -> dialog.dispose());
		
		buttonPanel.add(addBtn);
		buttonPanel.add(cancelBtn);
		mainPanel.add(buttonPanel, BorderLayout.SOUTH);
		
		dialog.add(mainPanel);
		dialog.setVisible(true);
	}
	
	
	/**
	 * Refresh the underlying list from the currently selected source
	 * (Base Game or a specific mod).
	 */
	private void refreshUnderlyingList() {
		underlyingItems.clear();
		
		if (activeSource == null) {
			// Base Game
			underlyingItems.addAll(Singleton.INSTANCE.game().getItems());
		} else {
			// Specific mod
			underlyingItems.addAll(activeSource.getItems());
		}
		
		// Sort by ID for consistent display
		underlyingItems.sort(Comparator.comparing(ModItem::getId, String.CASE_INSENSITIVE_ORDER));
	}
	
	/**
	 * Setup the list selection listener
	 */
	private void setupListSelectionListener() {
		// Remove any existing listeners to avoid duplicates
		for (ListSelectionListener listener : itemList.getListSelectionListeners()) {
			itemList.removeListSelectionListener(listener);
		}
		
		// Add a single, properly filtered listener
		itemList.addListSelectionListener(e -> {
			if (e.getValueIsAdjusting())
				return;
			
			int displayIndex = itemList.getSelectedIndex();
			if (displayIndex == - 1)
				return;
			
			// Get the underlying index from our mapping
			int underlyingIndex = displayToUnderlyingIndex.get(displayIndex);
			
			// Get the actual item from underlying list
			ModItem underlyingItem = underlyingItems.get(underlyingIndex);
			selectedItem = underlyingItem;
			
			// Update detail panel
			detailLabel.setText(underlyingItem.detailPanel());
			
			// NO AUTO COPY HERE - removed the automatic copy on selection
		});
	}
	
	/**
	 * Get the display string for an item
	 */
	private String getItemDisplayString(final ModItem item) {
		String id = item.getId();
		
		// Truncate long IDs for better display
		if (id.length() > 45) {
			id = id.substring(0, 42) + "...";
		}
		
		return id;
	}
	
	/**
	 * Refresh the display model based on current filters
	 */
	private void refreshDisplay(boolean tellUser) {
		final String filterText = search.getText().toLowerCase().trim();
		
		// Get selected item type filter
		final String selectedType = (String) itemTypeSelector.getSelectedItem();
		
		// Clear display model and mapping
		displayModel.clear();
		displayToUnderlyingIndex.clear();
		
		// Don't trigger selection events while rebuilding
		itemList.setValueIsAdjusting(true);
		
		final var typeMatch = ItemType.matchesItemType(selectedType);
		final boolean globalMach = filterText.equals("search items…");
		try {
			// Filter the underlying list by iterating through indices
			for (int i = 0; i < underlyingItems.size(); i++) {
				final ModItem item = underlyingItems.get(i);
				final String itemId = item.getId();
				
				// Check item type filter
				final boolean mach = typeMatch == null || typeMatch.test(item);
				final boolean textMach = globalMach || itemId.toLowerCase().contains(filterText);
				
				if (mach && textMach) {
					// Add to display model and store the underlying index
					displayModel.addElement(getItemDisplayString(item));
					displayToUnderlyingIndex.add(i);
				}
			}
		} finally {
			itemList.setValueIsAdjusting(false);
		}
		
		if (tellUser)
			window.snackbar.show("Showing " + displayModel.size() + " items", BarManager.Type.INFO);
	}
	
	
	/**
	 * Refresh all items (call this after mod changes).
	 * Also re-syncs the mod source dropdown in case mods were added or removed.
	 */
	public void refreshAllItems() {
		modSourceSelector.removeAllItems();
		modSourceSelector.addItem("🎮  Base Game");
		for (var mod : window.getRegistry().modService.modCollection) {
			modSourceSelector.addItem("📦  " + mod.name);
		}
		
		refreshUnderlyingList();
		refreshDisplay(true);
		repaint();
	}
}