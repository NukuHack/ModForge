package modforge.frontend.pages;

import modforge.backend.model.IModItem;
import modforge.backend.model.item.*;
import modforge.backend.service.*;
import modforge.frontend.*;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;

import static modforge.backend.Util.escapeHtml;

public class ItemsPage extends BasePage {
	// UNDERLYING DATA - the source of truth
	private final List<IModItem> underlyingItems = new ArrayList<>();

	// DISPLAY MODEL - filtered view of underlying items
	private final DefaultListModel<String> displayModel = new DefaultListModel<>();
	private final JList<String> itemList = new JList<>(displayModel);

	// Track indices in the underlying list
	private final List<Integer> displayToUnderlyingIndex = new ArrayList<>();

	private final JTextField search = styledField("Search items…");
	private final JComboBox<String> itemTypeSelector = new JComboBox<>();

	// Services
	private final ServiceRegistry registry;
	private final XmlService xmlService;

	// Detail panel components
	private JLabel detailLabel;
	private JPanel detailPanel;
	private IModItem currentSelectedItem;

	public ItemsPage(final MainWindow w) {
		super(w);
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

		// Get services from registry
		this.registry = w.getRegistry();
		this.xmlService = registry.xmlService;

		// Setup the list selection listener
		setupListSelectionListener();

		// Populate the underlying list with all items from services
		refreshUnderlyingList();

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
		filterPanel.add(itemTypeSelector, BorderLayout.WEST);
		filterPanel.add(search, BorderLayout.CENTER);

		top.add(leftPanel, BorderLayout.WEST);
		top.add(filterPanel, BorderLayout.CENTER);

		// Setup search filter
		search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			public void insertUpdate(DocumentEvent e) { refreshDisplay(); }
			public void removeUpdate(DocumentEvent e) { refreshDisplay(); }
			public void changedUpdate(DocumentEvent e) { refreshDisplay(); }
		});

		// Build main content panel (list + detail)
		JPanel mainContent = buildMainContentPanel();

		add(top, BorderLayout.NORTH);
		add(mainContent, BorderLayout.CENTER);
	}

	/**
	 * Setup item type selector for filtering by specific class
	 */
	private void setupItemTypeSelector() {
		String[] itemTypes = {
				"All Types",
				"Melee Weapons",
				"Missile Weapons",
				"Ammo",
				"Armor",
				"Helmet",
				"Hood",
				"Food",
				"Poison",
				"Herb",
				"Crafting Material",
				"Misc Item",
				"Key",
				"Money",
				"KeyRing",
				"Perk",
				"Buff"
		};

		for (String type : itemTypes) {
			itemTypeSelector.addItem(type);
		}

		itemTypeSelector.setFont(new Font("Roboto", Font.PLAIN, 12));
		itemTypeSelector.setBackground(MainWindow.SURFACE);
		itemTypeSelector.setForeground(MainWindow.TEXT);
		itemTypeSelector.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(0x2a2a3a)),
				BorderFactory.createEmptyBorder(4, 8, 4, 8)
		));

		itemTypeSelector.addActionListener(e -> refreshDisplay());
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
		detailPanel = new JPanel(new BorderLayout());
		detailPanel.setBackground(new Color(0x181825));
		detailPanel.setPreferredSize(new Dimension(400, 0));
		detailPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

		// Create detail label with HTML content
		detailLabel = new JLabel("<html><b>Select an item</b><br/><br/>" +
				"<span style='color:#6c6f85'>Details will appear here.</span></html>");
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
				if (currentSelectedItem != null && e.getY() < 40) {
					// Auto-copy ID when clicking on the blue ID text
					String id = currentSelectedItem.getId();
					Util.copyText(id);
					window.snackbar.show("ID copied to clipboard: " + id, BarManager.Type.INFO);
				}
			}
		});

		// Add right-click menu for copying all text
		JPopupMenu popupMenu = new JPopupMenu();
		JMenuItem copyAllMenuItem = new JMenuItem("Copy All Details");
		copyAllMenuItem.addActionListener(e -> {
			if (currentSelectedItem != null) {
				String allDetails = getAllItemDetailsAsText(currentSelectedItem);
				Util.copyText(allDetails);
				window.snackbar.show("All details copied to clipboard", BarManager.Type.INFO);
			}
		});
		popupMenu.add(copyAllMenuItem);

		JMenuItem copyIdMenuItem = new JMenuItem("Copy ID");
		copyIdMenuItem.addActionListener(e -> {
			if (currentSelectedItem != null) {
				Util.copyText(currentSelectedItem.getId());
				window.snackbar.show("ID copied to clipboard: " + currentSelectedItem.getId(), BarManager.Type.INFO);
			}
		});
		popupMenu.add(copyIdMenuItem);

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

	/**
	 * Get all item details as plain text for copying
	 */
	private String getAllItemDetailsAsText(IModItem item) {
		StringBuilder sb = new StringBuilder();
		sb.append("ID: ").append(item.getId()).append("\n");
		sb.append("Class: ").append(item.getClass().getSimpleName()).append("\n");
		sb.append("Path: ").append(item.getPath()).append("\n");

		// Show attributes if any
		if (!item.getAttributes().isEmpty()) {
			sb.append("\nAttributes:\n");
			for (var attr : item.getAttributes()) {
				sb.append("  • ").append(attr.getName()).append(": ").append(attr.getValue()).append("\n");
			}
		}

		// Show linked IDs if any
		if (!item.getLinkedIds().isEmpty()) {
			sb.append("\nLinked Items:\n");
			for (String linkedId : item.getLinkedIds()) {
				sb.append("  • ").append(linkedId).append("\n");
			}
		}

		return sb.toString();
	}

	/**
	 * Refresh the underlying list from services
	 */
	private void refreshUnderlyingList() {
		underlyingItems.clear();

		// Load all items from XmlService
		final List<IModItem> allItems = Stream.of(
				xmlService.perks.stream(),
				xmlService.buffs.stream(),
				xmlService.weapons.stream(),
				xmlService.armors.stream(),
				xmlService.consumables.stream(),
				xmlService.craftingMaterials.stream(),
				xmlService.miscItems.stream(),
				xmlService.weaponClasses.stream()
		).flatMap(s -> s).toList();

		underlyingItems.addAll(allItems);

		// Sort by ID for consistent display
		underlyingItems.sort(Comparator.comparing(IModItem::getId, String.CASE_INSENSITIVE_ORDER));
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
			if (e.getValueIsAdjusting()) return;

			int displayIndex = itemList.getSelectedIndex();
			if (displayIndex == -1) return;

			// Get the underlying index from our mapping
			int underlyingIndex = displayToUnderlyingIndex.get(displayIndex);

			// Get the actual item from underlying list
			IModItem underlyingItem = underlyingItems.get(underlyingIndex);
			currentSelectedItem = underlyingItem;

			// Update detail panel
			updateDetailPanel(underlyingItem);

			// NO AUTO COPY HERE - removed the automatic copy on selection
		});
	}

	/**
	 * Update the detail panel with the selected item's information
	 */
	private void updateDetailPanel(IModItem item) {
		// Build detailed information about the item
		StringBuilder details = new StringBuilder();
		details.append("<html><div style='font-family: monospace;'>");

		// Make ID clickable with a special style
		details.append("<div style='cursor: pointer; display: inline-block;' onclick='copyId()'>");
		details.append("<b style='color:#89b4fa; font-size:14px; text-decoration: underline; text-decoration-color: #89b4fa;'>")
				.append(escapeHtml(item.getId()))
				.append("</b>");
		details.append("</div>");
		details.append("<span style='color:#6c6f85; font-size: 10px; margin-left: 8px;'>(click to copy)</span>");
		details.append("<br/><br/>");
		details.append("<span style='color:#6c6f85'>");

		details.append("Class: ").append(item.getClass().getSimpleName()).append("<br/>");
		details.append("Path: ").append(escapeHtml(item.getPath())).append("<br/>");

		// Show attributes if any
		if (!item.getAttributes().isEmpty()) {
			details.append("<br/><b>Attributes:</b><br/>");
			for (var attr : item.getAttributes()) {
				details.append("• ").append(escapeHtml(attr.getName())).append(": ")
						.append(escapeHtml(String.valueOf(attr.getValue()))).append("<br/>");
			}
		}

		// Show linked IDs if any
		if (!item.getLinkedIds().isEmpty()) {
			details.append("<br/><b>Linked Items:</b><br/>");
			for (String linkedId : item.getLinkedIds()) {
				details.append("• ").append(escapeHtml(linkedId)).append("<br/>");
			}
		}

		details.append("</span></div></html>");
		detailLabel.setText(details.toString());
	}

	/**
	 * Get the display string for an item
	 */
	private String getItemDisplayString(IModItem item) {
		String icon = getIconForItem(item);
		String id = item.getId();

		// Truncate long IDs for better display
		if (id.length() > 45) {
			id = id.substring(0, 42) + "...";
		}

		return String.format("%s  %s", icon, id);
	}

	/**
	 * Get appropriate icon based on item type
	 */
	private String getIconForItem(IModItem item) {
		if (item instanceof MeleeWeapon) return "⚔️";
		if (item instanceof MissileWeapon) return "🏹";
		if (item instanceof Ammo) return "🎯";
		if (item instanceof Armor) return "🛡️";
		if (item instanceof Helmet) return "⛑️";
		if (item instanceof Hood) return "🧢";
		if (item instanceof Food) return "🍎";
		if (item instanceof Poison) return "☠️";
		if (item instanceof Herb) return "🌿";
		if (item instanceof CraftingMaterial) return "🔧";
		if (item instanceof MiscItem) return "📦";
		if (item instanceof Key) return "🔑";
		if (item instanceof Money) return "💰";
		if (item instanceof KeyRing) return "🔗";
		if (item instanceof Perk) return "⭐";
		if (item instanceof Buff) return "✨";
		return "📄";
	}

	/**
	 * Refresh the display model based on current filters
	 */
	private void refreshDisplay() {
		final String filterText = search.getText().toLowerCase().trim();

		// Get selected item type filter
		final String selectedType = (String) itemTypeSelector.getSelectedItem();

		// Clear display model and mapping
		displayModel.clear();
		displayToUnderlyingIndex.clear();

		// Don't trigger selection events while rebuilding
		itemList.setValueIsAdjusting(true);

		try {
			// Filter the underlying list by iterating through indices
			for (int i = 0; i < underlyingItems.size(); i++) {
				IModItem item = underlyingItems.get(i);
				String itemId = item.getId();

				// Check item type filter
				boolean typeMatch = matchesItemType(item, selectedType);

				// Check text filter
				boolean textMatch = filterText.isEmpty() || filterText.equals("search items…") || itemId.toLowerCase().contains(filterText);

				if (typeMatch && textMatch) {
					// Add to display model and store the underlying index
					displayModel.addElement(getItemDisplayString(item));
					displayToUnderlyingIndex.add(i);
				}
			}
		} finally {
			itemList.setValueIsAdjusting(false);
		}

		// Update status
		window.snackbar.show("Showing " + displayModel.size() + " items", BarManager.Type.INFO);
	}

	/**
	 * Check if an item matches the selected item type
	 */
	private boolean matchesItemType(IModItem item, String selectedType) {
		if (selectedType == null || selectedType.equals("All Types"))
			return true;

		return switch (selectedType) {
			case "Melee Weapons" -> item instanceof MeleeWeapon;
			case "Missile Weapons" -> item instanceof MissileWeapon;
			case "Ammo" -> item instanceof Ammo;
			case "Armor" -> item instanceof Armor;
			case "Helmet" -> item instanceof Helmet;
			case "Hood" -> item instanceof Hood;
			case "Food" -> item instanceof Food;
			case "Poison" -> item instanceof Poison;
			case "Herb" -> item instanceof Herb;
			case "Crafting Material" -> item instanceof CraftingMaterial;
			case "Misc Item" -> item instanceof MiscItem;
			case "Key" -> item instanceof Key;
			case "Money" -> item instanceof Money;
			case "KeyRing" -> item instanceof KeyRing;
			case "Perk" -> item instanceof Perk;
			case "Buff" -> item instanceof Buff;
			default -> true;
		};
	}

	/**
	 * Refresh all items (call this after mod changes)
	 */
	public void refreshAllItems() {
		refreshUnderlyingList();
		refreshDisplay();
		repaint();
	}
}