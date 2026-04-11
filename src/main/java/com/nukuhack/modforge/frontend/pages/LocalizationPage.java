package com.nukuhack.modforge.frontend.pages;

import com.nukuhack.modforge.Singleton;
import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.backend.ModData;
import com.nukuhack.modforge.backend.model.ModItem;
import com.nukuhack.modforge.frontend.BarManager;
import com.nukuhack.modforge.frontend.MainWindow;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static com.nukuhack.modforge.Util.escHtml;
import static com.nukuhack.modforge.Util.unescapeXml;
import static com.nukuhack.modforge.frontend.MainWindow.getLocalText;

@Slf4j
public class LocalizationPage extends BasePage {
	
	private static final String ALL_ATTR_TYPES = "All Attributes";
	
	private static final List<String> ATTR_TYPE_OPTIONS;
	
	static {
		List<String> sorted = new ArrayList<>(ModItem.LANG_FIELD_HINTS);
		Collections.sort(sorted);
		sorted.add(0, ALL_ATTR_TYPES);
		ATTR_TYPE_OPTIONS = Collections.unmodifiableList(sorted);
	}
	
	private final JComboBox<String> attrSelector = new JComboBox<>();
	
	private final List<LangEntry> allEntries = new ArrayList<>();
	private final DefaultListModel<String> listModel = new DefaultListModel<>();
	private final JList<String> entryList = new JList<>(listModel);
	
	private final JEditorPane detailPane = new JEditorPane();
	
	private int[] filteredIndices = new int[0];
	private LangEntry selectedEntry = null;
	
	public LocalizationPage(MainWindow w) {
		super(w);
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
		setLayout(new BorderLayout(0, 12));
		add(buildTopBar(), BorderLayout.NORTH);
		add(buildCenter(), BorderLayout.CENTER);
	}
	
	private static String formatRow(LangEntry le) {
		String key = le.langKey;
		if (key.length() > 48)
			key = key.substring(0, 45) + "…";
		
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("%-50s", key));
		if (! le.attrName.isBlank())
			sb.append("  [").append(le.attrName).append("]");
		if (le.item != null) {
			String itemId = le.item.getId();
			if (itemId.length() > 30)
				itemId = itemId.substring(0, 27) + "…";
			sb.append("  ·  ").append(itemId);
		}
		return sb.toString();
	}
	
	private static String emptyDetailHtml() {
		return "<html><body style='background:#181825;color:#6c6f85;" + "font-family:sans-serif;padding:16px;'>" + "<i>" + getLocalText("ui_select_entry") + "</i></body></html>";
	}
	
	private static String buildDetailHtml(LangEntry le) {
		StringBuilder html = new StringBuilder();
		html.append("<html><body style='background:#181825;color:#cdd6f4;" + "font-family:sans-serif;padding:14px;'>");
		
		html.append("<b style='color:#89b4fa;font-size:13px;'>").append(escHtml(unescapeXml(le.langKey))).append("</b>");
		html.append("<hr style='border-color:#313244;margin:8px 0;'/>");
		
		if (! le.attrName.isBlank()) {
			html.append("<div style='margin-bottom:8px;'>");
			html.append("<span style='color:#6c6f85;font-size:10px;'>").append(getLocalText("ui_attribute")).append("</span><br/>");
			html.append("<span style='color:#cba6f7;'>").append(escHtml(unescapeXml(le.attrName))).append("</span>");
			html.append("</div>");
		}
		
		html.append("<div style='margin-bottom:10px;'>");
		html.append("<span style='color:#6c6f85;font-size:10px;'>").append(getLocalText("ui_value")).append("</span><br/>");
		html.append("<span style='color:#a6e3a1;font-size:13px;'>").append(escHtml(unescapeXml(le.value))).append("</span>");
		html.append("</div>");
		
		if (le.item != null) {
			html.append("<hr style='border-color:#313244;margin:8px 0;'/>");
			html.append("<span style='color:#6c6f85;font-size:10px;'>").append(getLocalText("ui_item_context")).append("</span><br/><br/>");
			
			html.append("<div style='margin-bottom:6px;'>");
			html.append("<span style='color:#6c6f85;font-size:10px;'>").append(getLocalText("ui_id")).append("</span><br/>");
			html.append("<span style='color:#89b4fa;font-family:monospace;font-size:11px;'>").append(escHtml(le.item.getId())).append("</span>");
			html.append("</div>");
			
			html.append("<div style='margin-bottom:6px;'>");
			html.append("<span style='color:#6c6f85;font-size:10px;'>").append(getLocalText("ui_type")).append("</span><br/>");
			html.append("<span style='color:#cdd6f4;font-size:11px;'>").append(escHtml(le.item.getClass().getSimpleName())).append("</span>");
			html.append("</div>");
			
			html.append("<div style='margin-bottom:6px;'>");
			html.append("<span style='color:#6c6f85;font-size:10px;'>").append(getLocalText("ui_path")).append("</span><br/>");
			html.append("<span style='color:#89b4fa;font-family:monospace;font-size:10px;'>").append(escHtml(le.item.getPath())).append("</span>");
			html.append("</div>");
		}
		
		html.append("</body></html>");
		return html.toString();
	}
	
	private static Map<String, LangEntry> getLangEntryMap(Map<String, String> langMap, ModData source) {
		final Map<String, LangEntry> entryByKey = new LinkedHashMap<>(langMap.size());
		for (var e : langMap.entrySet())
			entryByKey.put(e.getKey(), new LangEntry(e.getKey(), e.getValue(), "", null));
		
		for (var item : source.getItems()) {
			for (var attr : item.getLangAttributes()) {
				var key = attr.getValue();
				if (key.isEmpty())
					continue;
				var existing = entryByKey.get(key);
				if (existing != null && existing.item == null)
					entryByKey.put(key, new LangEntry(existing.langKey, existing.value, attr.getName(), item));
			}
		}
		log.debug("set: {}", entryByKey.values().stream().map(l -> l.attrName).collect(Collectors.toSet()));
		return entryByKey;
	}
	
	@Override
	public void refresh(Object... input) {
		refreshModSelector();
		refreshLangSelector();
		refreshAll();
	}
	
	private JPanel buildTopBar() {
		var bar = new JPanel(new BorderLayout(12, 0));
		bar.setOpaque(false);
		bar.add(header("ui_local_loaded"), BorderLayout.WEST);
		
		var controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		controls.setOpaque(false);
		
		styleCombo(modSelector);
		modSelector.setPreferredSize(new Dimension(200, 28));
		modSelector.setToolTipText(getLocalText("ui_mod_source_tip"));
		modSelector.addActionListener(e -> refreshAll());
		
		styleCombo(langSelector);
		langSelector.setPreferredSize(new Dimension(130, 28));
		langSelector.setToolTipText(getLocalText("ui_language_tip"));
		langSelector.addActionListener(e -> refreshAll());
		
		for (var opt : ATTR_TYPE_OPTIONS)
			attrSelector.addItem(opt);
		styleCombo(attrSelector);
		attrSelector.setPreferredSize(new Dimension(170, 28));
		attrSelector.setToolTipText(getLocalText("ui_filter_by_attribute_tip"));
		attrSelector.addActionListener(e -> applyFilters());
		
		search.setPreferredSize(new Dimension(220, 28));
		search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			public void insertUpdate(DocumentEvent e) {
				applyFilters();
			}
			
			public void removeUpdate(DocumentEvent e) {
				applyFilters();
			}
			
			public void changedUpdate(DocumentEvent e) {
				applyFilters();
			}
		});
		
		controls.add(muted("ui_mod"));
		controls.add(modSelector);
		controls.add(muted("ui_lang"));
		controls.add(langSelector);
		controls.add(muted("ui_type"));
		controls.add(attrSelector);
		controls.add(search);
		
		bar.add(controls, BorderLayout.EAST);
		return bar;
	}
	
	private JSplitPane buildCenter() {
		entryList.setBackground(new Color(0x181825));
		entryList.setForeground(MainWindow.TEXT);
		entryList.setSelectionBackground(new Color(0x313244));
		entryList.setSelectionForeground(MainWindow.TEXT);
		entryList.setFont(new Font("Roboto Mono", Font.PLAIN, 12));
		entryList.setFixedCellHeight(30);
		entryList.addListSelectionListener(e -> {
			if (e.getValueIsAdjusting())
				return;
			int idx = entryList.getSelectedIndex();
			if (idx < 0 || idx >= filteredIndices.length)
				return;
			selectedEntry = allEntries.get(filteredIndices[idx]);
			detailPane.setText(buildDetailHtml(selectedEntry));
			detailPane.setCaretPosition(0);
		});
		
		entryList.setComponentPopupMenu(buildEntryPopupMenu());
		
		entryList.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && selectedEntry != null) {
					Util.copyText(selectedEntry.langKey);
					window.snackbar.show("ui_copied_key", BarManager.Type.INFO, selectedEntry.langKey);
				}
			}
		});
		
		var listScroll = new JScrollPane(entryList);
		listScroll.setBackground(MainWindow.SURFACE);
		listScroll.getViewport().setBackground(new Color(0x181825));
		listScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(0x313244)), getLocalText("ui_entries"), javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP, new Font("Roboto", Font.BOLD, 12), MainWindow.ACCENT));
		
		detailPane.setContentType("text/html");
		detailPane.setEditable(false);
		detailPane.setBackground(new Color(0x181825));
		detailPane.setForeground(MainWindow.TEXT);
		detailPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
		detailPane.setText(emptyDetailHtml());
		
		var detailScroll = new JScrollPane(detailPane);
		detailScroll.setBackground(MainWindow.SURFACE);
		detailScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(0x313244)), getLocalText("ui_detail"), javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP, new Font("Roboto", Font.BOLD, 12), MainWindow.ACCENT));
		
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, detailScroll);
		split.setResizeWeight(0.55);
		split.setDividerSize(4);
		split.setBackground(MainWindow.BG);
		split.setBorder(BorderFactory.createEmptyBorder());
		return split;
	}
	
	/** Popup specific to the localization entry list. */
	private JPopupMenu buildEntryPopupMenu() {
		var popup = new JPopupMenu();
		
		var copyKey = new JMenuItem(getLocalText("ui_copy_key"));
		copyKey.addActionListener(e -> {
			if (selectedEntry != null) {
				Util.copyText(selectedEntry.langKey);
				window.snackbar.show("ui_copied_key", BarManager.Type.INFO, selectedEntry.langKey);
			}
		});
		
		var copyVal = new JMenuItem(getLocalText("ui_copy_value"));
		copyVal.addActionListener(e -> {
			if (selectedEntry != null) {
				Util.copyText(selectedEntry.value);
				window.snackbar.show("ui_copied_value", BarManager.Type.INFO);
			}
		});
		
		var goToItem = new JMenuItem(getLocalText("ui_go_to_item_edit"));
		goToItem.addActionListener(e -> {
			if (selectedEntry != null && selectedEntry.item != null)
				window.navigate(MainWindow.Page.LANG_EDIT, selectedEntry.item);
		});
		
		popup.add(copyKey);
		popup.add(copyVal);
		popup.addSeparator();
		popup.add(goToItem);
		return popup;
	}
	
	private void refreshAll() {
		allEntries.clear();
		selectedEntry = null;
		detailPane.setText(emptyDetailHtml());
		
		var source = getSelectedMod();
		var lang = getSelectedLang().orElseGet(Singleton.INSTANCE.getRegistry().userConfig::getLanguage);
		
		var mod = source.orElseGet(Singleton.INSTANCE::getGame);
		var langMap = mod.getLang(lang);
		if (langMap.isEmpty()) {
			applyFilters();
			window.snackbar.show("ui_no_localization_data", BarManager.Type.WARNING, lang.getDisplayName());
			return;
		}
		
		allEntries.addAll(getLangEntryMap(langMap, mod).values());
		allEntries.sort(Comparator.comparing((LangEntry le) -> le.attrName, Comparator.nullsLast(String::compareToIgnoreCase)).thenComparing(le -> le.langKey, String.CASE_INSENSITIVE_ORDER));
		
		applyFilters();
		window.snackbar.show("ui_localization_loaded", BarManager.Type.SUCCESS, allEntries.size());
	}
	
	private void applyFilters() {
		var rawSearch = search.getText().trim().toLowerCase(Locale.ROOT);
		var placeholder = getLocalText("ui_search_all").toLowerCase(Locale.ROOT);
		var noSearch = rawSearch.isEmpty() || rawSearch.equals(placeholder);
		var attrFilter = (String) attrSelector.getSelectedItem();
		var allAttrs = attrFilter == null || attrFilter.equals(ALL_ATTR_TYPES);
		
		var indices = new int[allEntries.size()];
		int count = 0;
		for (int i = 0; i < allEntries.size(); i++) {
			var entry = allEntries.get(i);
			if ((allAttrs || entry.attrName.startsWith(attrFilter)) && (noSearch || entry.has(rawSearch)))
				indices[count++] = i;
		}
		filteredIndices = Arrays.copyOf(indices, count);
		
		entryList.setValueIsAdjusting(true);
		listModel.clear();
		for (var idx : filteredIndices)
			listModel.addElement(formatRow(allEntries.get(idx)));
		entryList.setValueIsAdjusting(false);
	}
	
	record LangEntry(String langKey, String value, String attrName, ModItem item) {
		public boolean has(String inp) {
			return langKey.toLowerCase(Locale.ROOT).contains(inp) || value.toLowerCase(Locale.ROOT).contains(inp);
		}
	}
}