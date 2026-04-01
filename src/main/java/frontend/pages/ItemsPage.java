package frontend.pages;

import frontend.*;
import javax.swing.*;
import java.awt.*;
import javax.swing.event.ListSelectionListener;

// =============================================================================
//  ITEMS PAGE
// =============================================================================
public class ItemsPage extends BasePage {
	private final DefaultListModel<String> listModel = new DefaultListModel<>();
	private final JList<String> itemList = new JList<>(listModel);
	private final JTextField search = styledField("Search items…");
	private String lastCopiedId = null; // Track last copied ID to prevent duplicates
	private long lastCopyTime = 0; // Track last copy time

	public ItemsPage(MainWindow w) {
		super(w);
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

		// Category tabs
		String[] cats = {"All", "Weapons", "Armor", "Consumables", "Perks", "Buffs", "Misc"};
		JTabbedPane tabs = new JTabbedPane();
		tabs.setFont(new Font("Roboto", Font.PLAIN, 12));
		tabs.setForeground(MainWindow.TEXT);
		tabs.setBackground(MainWindow.SURFACE);

		// Setup the list selection listener ONCE before adding tabs
		setupListSelectionListener();

		for (String cat : cats) {
			JPanel p = buildCategoryPanel(cat);
			tabs.addTab(cat, p);
		}

		// Populate with dummies – replace with xmlService data
		populateList();

		JPanel top = new JPanel(new BorderLayout(12, 0));
		top.setOpaque(false);
		top.add(header("Game Items"), BorderLayout.WEST);
		top.add(search, BorderLayout.CENTER);

		search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			public void insertUpdate(javax.swing.event.DocumentEvent e) {
				filter();
			}

			public void removeUpdate(javax.swing.event.DocumentEvent e) {
				filter();
			}

			public void changedUpdate(javax.swing.event.DocumentEvent e) {
				filter();
			}
		});

		add(top, BorderLayout.NORTH);
		add(tabs, BorderLayout.CENTER);
	}

	/**
	 * Setup the list selection listener with proper duplicate prevention
	 */
	private void setupListSelectionListener() {
		// Remove any existing listeners to avoid duplicates
		for (ListSelectionListener listener : itemList.getListSelectionListeners()) {
			itemList.removeListSelectionListener(listener);
		}

		// Add a single, properly filtered listener
		itemList.addListSelectionListener(e -> {
			// CRITICAL: Only process when selection is done adjusting
			if (e.getValueIsAdjusting()) {
				return;
			}

			String selectedValue = itemList.getSelectedValue();
			if (selectedValue == null) {
				return;
			}

			// Get the actual ID (remove the icon prefix)
			String id = selectedValue.replaceFirst("^[^\\s]+\\s+", "").trim();

			// Get the current tab/category
			JTabbedPane tabs = (JTabbedPane) getComponent(1); // tabs is the second component added
			String category = tabs.getTitleAt(tabs.getSelectedIndex());

			// Update detail panel
			updateDetailPanel(id, category);

			// Debounce the copy operation - only copy if:
			// 1. It's a different ID, OR
			// 2. More than 500ms has passed since last copy of same ID
			long now = System.currentTimeMillis();
			if (!id.equals(lastCopiedId) || (now - lastCopyTime) > 500) {
				Util.copyText(id);
				lastCopiedId = id;
				lastCopyTime = now;
				window.snackbar.show("ID copied to clipboard: " + id, BarManager.Type.INFO);
			}
		});
	}

	/**
	 * Update the detail panel with the selected item's information
	 */
	private void updateDetailPanel(String id, String category) {
		// Find the detail label in the current visible panel
		JTabbedPane tabs = (JTabbedPane) getComponent(1);
		Component selectedTab = tabs.getSelectedComponent();

		if (selectedTab instanceof JPanel) {
			JPanel panel = (JPanel) selectedTab;
			// Navigate through the component hierarchy to find the detail label
			Component[] components = panel.getComponents();
			for (Component comp : components) {
				if (comp instanceof JSplitPane) {
					JSplitPane split = (JSplitPane) comp;
					Component rightComponent = split.getRightComponent();
					if (rightComponent instanceof JPanel) {
						JPanel detailPanel = (JPanel) rightComponent;
						Component[] detailComponents = detailPanel.getComponents();
						for (Component detailComp : detailComponents) {
							if (detailComp instanceof JLabel) {
								JLabel detailLabel = (JLabel) detailComp;
								detailLabel.setText("<html><b>" + id + "</b><br/><br/>" +
										"<span style='color:#6c6f85'>ID: " + id + "<br/>Type: " +
										category + "</span></html>");
								break;
							}
						}
						break;
					}
				}
			}
		}
	}

	private JPanel buildCategoryPanel(String cat) {
		JPanel p = new JPanel(new BorderLayout());
		p.setBackground(MainWindow.BG);

		// Note: The list selection listener is already set up globally
		// Don't add another one here

		JScrollPane scroll = new JScrollPane(itemList);
		scroll.setBackground(MainWindow.SURFACE);
		scroll.setBorder(BorderFactory.createEmptyBorder());

		JPanel detail = new JPanel(new BorderLayout());
		detail.setBackground(new Color(0x181825));
		detail.setPreferredSize(new Dimension(320, 0));
		detail.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
		JLabel detailLabel = new JLabel("<html><b>Select an item</b><br/><br/><span style='color:#6c6f85'>Details will appear here.</span></html>");
		detailLabel.setForeground(MainWindow.TEXT);
		detail.add(detailLabel, BorderLayout.NORTH);

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scroll, detail);
		split.setDividerLocation(560);
		split.setBackground(MainWindow.BG);

		p.add(split, BorderLayout.CENTER);
		return p;
	}

	private void populateList() {
		listModel.clear();
		for (int i = 1; i <= 20; i++) listModel.addElement("🗡  item_sword_" + i);
		for (int i = 1; i <= 10; i++) listModel.addElement("🛡  item_armor_" + i);
	}

	private void filter() {
		final String q = search.getText().toLowerCase();
		// Real implementation: filter against xmlService collections
		listModel.clear();

		// Don't trigger selection events while clearing/repopulating
		itemList.setValueIsAdjusting(true);

		try {
			if (q.isBlank() || q.equals("search items…")) {
				populateList();
			} else {
				for (int i = 1; i <= 20; i++) {
					String s = "item_sword_" + i;
					if (s.contains(q)) listModel.addElement("🗡  " + s);
				}
				for (int i = 1; i <= 10; i++) {
					String s = "item_armor_" + i;
					if (s.contains(q)) listModel.addElement("🛡  " + s);
				}
			}
		} finally {
			itemList.setValueIsAdjusting(false);
		}
	}
}