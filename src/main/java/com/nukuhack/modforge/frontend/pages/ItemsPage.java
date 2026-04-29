package com.nukuhack.modforge.frontend.pages;

import com.nukuhack.modforge.Singleton;
import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.backend.ItemType;
import com.nukuhack.modforge.backend.model.ModItem;
import com.nukuhack.modforge.frontend.BarManager;
import com.nukuhack.modforge.frontend.MainWindow;
import com.nukuhack.modforge.frontend.Page;
import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static com.nukuhack.modforge.frontend.MainWindow.getLocalText;

@Slf4j
@ExtensionMethod({ Util.class })
public class ItemsPage extends BasePage {
	
	private final List<ModItem> underlyingItems = new ArrayList<>();
	
	private final DefaultListModel<String> displayModel = new DefaultListModel<>();
	private final JList<String> itemList = new JList<>(displayModel);
	
	private final List<Integer> displayToUnderlyingIndex = new ArrayList<>();
	private final JComboBox<String> itemTypeSelector = new JComboBox<>();
	
	private JEditorPane detailPane;
	private ModItem selectedItem;
	
	public ItemsPage(final MainWindow w) {
		super(w);
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
		
		setupListSelectionListener();
		
		var top = new JPanel(new BorderLayout(12, 0));
		top.setOpaque(false);
		
		var leftPanel = new JPanel(new BorderLayout(8, 0));
		leftPanel.setOpaque(false);
		leftPanel.add(header("ui_items_loaded"), BorderLayout.WEST);
		
		var filterPanel = new JPanel(new BorderLayout(8, 0));
		filterPanel.setOpaque(false);
		
		setupItemTypeSelector();
		setupModSourceSelector();
		
		var selectorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		selectorPanel.setOpaque(false);
		selectorPanel.add(modSelector);
		selectorPanel.add(itemTypeSelector);
		
		filterPanel.add(selectorPanel, BorderLayout.WEST);
		filterPanel.add(search, BorderLayout.CENTER);
		
		top.add(leftPanel, BorderLayout.WEST);
		top.add(filterPanel, BorderLayout.CENTER);
		
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
		
		add(top, BorderLayout.NORTH);
		add(buildMainContentPanel(), BorderLayout.CENTER);
	}

	@Override
	public void refresh(Page source, Object... input) {
		super.refresh(source, input);
		
		refreshModSelector();
		refreshUnderlyingList();
		refreshDisplay(true);
		repaint();
	}
	
	private void setupModSourceSelector() {
		styleCombo(modSelector);
		modSelector.setPreferredSize(new Dimension(200, 28));
		modSelector.setToolTipText(getLocalText("ui_mod_source_tip"));
		itemTypeSelector.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x2a2a3a)), BorderFactory.createEmptyBorder(4, 8, 4, 8)));
		
		modSelector.addActionListener(e -> {
			refreshUnderlyingList();
			refreshDisplay(false);
		});
	}
	
	private void setupItemTypeSelector() {
		for (var type : ItemType.getAllTypes())
			itemTypeSelector.addItem(type);
		styleCombo(itemTypeSelector);
		itemTypeSelector.addActionListener(e -> refreshDisplay(true));
	}
	
	private JPanel buildMainContentPanel() {
		var mainPanel = new JPanel(new BorderLayout());
		mainPanel.setBackground(MainWindow.BG);
		
		var scroll = new JScrollPane(itemList);
		scroll.setBackground(MainWindow.SURFACE);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getViewport().setBackground(MainWindow.SURFACE);
		
		itemList.setBackground(new Color(0x181825));
		itemList.setForeground(MainWindow.TEXT);
		itemList.setSelectionBackground(new Color(0x313244));
		itemList.setSelectionForeground(MainWindow.TEXT);
		itemList.setFont(new Font("Roboto", Font.PLAIN, 12));
		itemList.setFixedCellHeight(28);
		
		detailPane = new JEditorPane();
		detailPane.setContentType("text/html");
		detailPane.setEditable(false);
		detailPane.setBackground(new Color(0x181825));
		detailPane.setForeground(MainWindow.TEXT);
		detailPane.setFont(new Font("Roboto", Font.PLAIN, 12));
		detailPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
		detailPane.setText("<html><body style='background:#181825;color:#6c6f85;" + "font-family:sans-serif;padding:12px;'>" + "<b style='color:#cdd6f4;'>" + getLocalText("ui_select_item") + "</b><br/><br/>" + "<i>" + getLocalText("ui_details_appear_here") + "</i></body></html>");
		
		detailPane.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && selectedItem != null) {
					Util.copyText(selectedItem.getId());
					window.snackbar.show("ui_copied_id", BarManager.Type.INFO, selectedItem.getId());
				}
			}
		});
		
		detailPane.setComponentPopupMenu(buildItemPopupMenu(() -> selectedItem, true, true));
		
		var detailScroll = new JScrollPane(detailPane);
		detailScroll.setPreferredSize(new Dimension(400, 0));
		detailScroll.setBackground(new Color(0x181825));
		detailScroll.getViewport().setBackground(new Color(0x181825));
		detailScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(0x313244)), getLocalText("ui_detail"), javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP, new Font("Roboto", Font.BOLD, 12), MainWindow.ACCENT));
		detailScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		detailScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		
		var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scroll, detailScroll);
		split.setDividerLocation(500);
		split.setBackground(MainWindow.BG);
		split.setDividerSize(4);
		split.setBorder(BorderFactory.createEmptyBorder());
		
		mainPanel.add(split, BorderLayout.CENTER);
		return mainPanel;
	}
	
	private void refreshUnderlyingList() {
		underlyingItems.clear();
		var mod = getSelectedMod();
		if (mod.isPresent()) {
			
			underlyingItems.addAll(mod.get().getItems());
		} else {
			
			var game = Singleton.getGame();
			underlyingItems.addAll(game.getItems());
		}
		underlyingItems.sort(Comparator.comparing(ModItem::getId, String.CASE_INSENSITIVE_ORDER));
	}
	
	private void setupListSelectionListener() {
		for (ListSelectionListener listener : itemList.getListSelectionListeners())
			itemList.removeListSelectionListener(listener);
		
		itemList.addListSelectionListener(e -> {
			if (e.getValueIsAdjusting())
				return;
			int displayIndex = itemList.getSelectedIndex();
			if (displayIndex < 0 || displayIndex >= displayToUnderlyingIndex.size())
				return;
			int underlyingIndex = displayToUnderlyingIndex.get(displayIndex);
			if (underlyingIndex < 0 || underlyingIndex >= underlyingItems.size())
				return;
			selectedItem = underlyingItems.get(underlyingIndex);
			if (detailPane != null) {
				detailPane.setText(htmlForItem(selectedItem));
				detailPane.setCaretPosition(0);
			}
		});
	}

	private void deleteCurrentItem() {
		if (selectedItem == null) {
			window.snackbar.show("ui_no_item_selected", BarManager.Type.WARNING);
			return;
		}
		final var targetMod = getSelectedMod();
		if (targetMod.isEmpty()) {
			window.snackbar.show("ui_select_mod_first", BarManager.Type.WARNING);
			return;
		}
		var mod = targetMod.get();
		if (!mod.containsItem(selectedItem)) {
			window.snackbar.show("ui_item_not_in_mod", BarManager.Type.WARNING);
			return;
		}
		int choice = JOptionPane.showConfirmDialog(
				this,
				MainWindow.getLocalText("ui_delete_item_confirm", selectedItem.getId()),
				MainWindow.getLocalText("ui_delete_item_title"),
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE
		);
		if (choice != JOptionPane.YES_OPTION) return;

		mod.getItems().remove(selectedItem);
		window.snackbar.show("ui_item_deleted", BarManager.Type.SUCCESS, selectedItem.getId());
		this.refresh(sourcePage, (Object) null);
	}
	
	private String getItemDisplayString(final ModItem item) {
		var id = item.getId();
		if (id.length() > 45)
			return id.substring(0, 42) + "...";
		return id;
	}
	
	private void refreshDisplay(boolean tellUser) {
		var rawSearch = search.getText().trim().toLowerCase(Locale.ROOT);
		
		var noSearch = rawSearch.isEmpty() || rawSearch.equals(getLocalText("ui_search_all").toLowerCase(Locale.ROOT));
		var selectedType = (String) itemTypeSelector.getSelectedItem();
		
		displayModel.clear();
		displayToUnderlyingIndex.clear();
		selectedItem = null;
		
		itemList.setValueIsAdjusting(true);
		var typeMatch = ItemType.matchesItemType(selectedType);
		try {
			for (int i = 0; i < underlyingItems.size(); i++) {
				var item = underlyingItems.get(i);
				var mach = typeMatch == null || typeMatch.test(item);
				var textMach = noSearch || item.getId().toLowerCase(Locale.ROOT).contains(rawSearch);
				if (mach && textMach) {
					displayModel.addElement(getItemDisplayString(item));
					displayToUnderlyingIndex.add(i);
				}
			}
		} finally {
			itemList.setValueIsAdjusting(false);
		}
		
		if (tellUser) {
			if (displayModel.isEmpty())
				window.snackbar.show("ui_no_items_to_display", BarManager.Type.WARNING);
			else
				window.snackbar.show("ui_showing_items", BarManager.Type.SUCCESS, displayModel.size());
		}
	}
}