package com.nukuhack.modforge.frontend.pages;

import com.nukuhack.modforge.Singleton;
import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.backend.ModData;
import com.nukuhack.modforge.backend.model.E.Language;
import com.nukuhack.modforge.backend.model.ModItem;
import com.nukuhack.modforge.backend.service.ModService;
import com.nukuhack.modforge.frontend.BarManager;
import com.nukuhack.modforge.frontend.MainWindow;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

import static com.nukuhack.modforge.Util.escHtml;
import static com.nukuhack.modforge.Util.unescapeXml;

// =============================================================================
//  LOCALIZATION PAGE
// =============================================================================
@lombok.extern.slf4j.Slf4j
public class LocalizationPage extends BasePage {
	
	// ── Selector: "All Attributes" + one entry per LANG_FIELD_HINTS value ─────
	private static final String ALL_ATTR_TYPES = "All Attributes";
	/**
	 * Sorted list of attribute-hint names shown in the type dropdown.
	 * Sourced directly from {@link ModItem#LANG_FIELD_HINTS}.
	 */
	private static final List<String> ATTR_TYPE_OPTIONS;
	
	static {
		List<String> sorted = new ArrayList<>(ModItem.LANG_FIELD_HINTS);
		Collections.sort(sorted);
		sorted.add(0, ALL_ATTR_TYPES);
		ATTR_TYPE_OPTIONS = Collections.unmodifiableList(sorted);
	}
	
	// ── Selectors ─────────────────────────────────────────────────────────────
	private final JComboBox<String> modSelector = new JComboBox<>();
	private final JComboBox<String> langSelector = new JComboBox<>();
	private final JComboBox<String> attrSelector = new JComboBox<>();
	private final JTextField search = styledField("Search keys or values…");
	
	// ── List data ─────────────────────────────────────────────────────────────
	/** Flat, ordered working set: one Entry per resolved localization string. */
	private final List<LangEntry> allEntries = new ArrayList<>();
	private final DefaultListModel<String> listModel = new DefaultListModel<>();
	private final JList<String> entryList = new JList<>(listModel);
	// ── Detail pane ───────────────────────────────────────────────────────────
	private final JEditorPane detailPane = new JEditorPane();
	/** Indices into allEntries that survive the current filter pass. */
	private int[] filteredIndices = new int[0];
	// ── Active selection ──────────────────────────────────────────────────────
	private LangEntry selectedEntry = null;
	
	// =========================================================================
	//  Constructor
	// =========================================================================
	
	public LocalizationPage(MainWindow w) {
		super(w);
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
		setLayout(new BorderLayout(0, 12));
		
		add(buildTopBar(), BorderLayout.NORTH);
		add(buildCenter(), BorderLayout.CENTER);
	}
	
	// =========================================================================
	//  BasePage contract
	// =========================================================================
	
	/**
	 * Single list-row: {@code <langKey>   (<attrName>  ·  <itemId>)}
	 * Padded so the context columns align reasonably well.
	 */
	private static String formatRow(LangEntry le) {
		String key = le.langKey;
		if (key.length() > 48)
			key = key.substring(0, 45) + "…";
		
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("%-50s", key));
		
		if (! le.attrName.isBlank()) {
			sb.append("  [").append(le.attrName).append("]");
		}
		if (le.item != null) {
			String itemId = le.item.getId();
			if (itemId.length() > 30)
				itemId = itemId.substring(0, 27) + "…";
			sb.append("  ·  ").append(itemId);
		}
		return sb.toString();
	}
	
	// =========================================================================
	//  Top bar
	// =========================================================================
	
	private static String emptyDetailHtml() {
		return "<html><body style='background:#181825;color:#6c6f85;" + "font-family:sans-serif;padding:16px;'>" + "<i>Select an entry to see its details.</i></body></html>";
	}
	
	// =========================================================================
	//  Center split-pane
	// =========================================================================
	
	private static String buildDetailHtml(LangEntry le) {
		StringBuilder html = new StringBuilder();
		html.append("<html><body style='background:#181825;color:#cdd6f4;" + "font-family:sans-serif;padding:14px;'>");
		
		// ── Lang key ──────────────────────────────────────────────────────
		html.append("<b style='color:#89b4fa;font-size:13px;'>").append(escHtml(unescapeXml(le.langKey))).append("</b>");
		html.append("<hr style='border-color:#313244;margin:8px 0;'/>");
		
		// ── Attribute hint name ───────────────────────────────────────────
		if (! le.attrName.isBlank()) {
			html.append("<div style='margin-bottom:8px;'>");
			html.append("<span style='color:#6c6f85;font-size:10px;'>attribute</span><br/>");
			html.append("<span style='color:#cba6f7;'>").append(escHtml(unescapeXml(le.attrName))).append("</span>");
			html.append("</div>");
		}
		
		// ── Value ─────────────────────────────────────────────────────────
		html.append("<div style='margin-bottom:10px;'>");
		html.append("<span style='color:#6c6f85;font-size:10px;'>value</span><br/>");
		html.append("<span style='color:#a6e3a1;font-size:13px;'>").append(escHtml(unescapeXml(le.value))).append("</span>");
		html.append("</div>");
		
		// ── Item context ──────────────────────────────────────────────────
		if (le.item != null) {
			html.append("<hr style='border-color:#313244;margin:8px 0;'/>");
			html.append("<span style='color:#6c6f85;font-size:10px;'>ITEM CONTEXT</span><br/><br/>");
			
			html.append("<div style='margin-bottom:6px;'>");
			html.append("<span style='color:#6c6f85;font-size:10px;'>id</span><br/>");
			html.append("<span style='color:#89b4fa;font-family:monospace;font-size:11px;'>").append(escHtml(le.item.getId())).append("</span>");
			html.append("</div>");
			
			html.append("<div style='margin-bottom:6px;'>");
			html.append("<span style='color:#6c6f85;font-size:10px;'>type</span><br/>");
			html.append("<span style='color:#cdd6f4;font-size:11px;'>").append(escHtml(le.item.getClass().getSimpleName())).append("</span>");
			html.append("</div>");
			
			html.append("<div style='margin-bottom:6px;'>");
			html.append("<span style='color:#6c6f85;font-size:10px;'>path</span><br/>");
			html.append("<span style='color:#89b4fa;font-family:monospace;font-size:10px;'>").append(escHtml(le.item.getPath())).append("</span>");
			html.append("</div>");
		}
		
		html.append("</body></html>");
		return html.toString();
	}
	
	// =========================================================================
	//  Selector population helpers
	// =========================================================================
	
	private static Map<String, LangEntry> getLangEntryMap(Map<String, String> langMap, ModData source) {
		final Map<String, LangEntry> entryByKey = new LinkedHashMap<>(langMap.size());
		for (var e : langMap.entrySet()) {
			// attrName and item left empty for now; filled below via putIfAbsent logic
			entryByKey.put(e.getKey(), new LangEntry(e.getKey(), e.getValue(), "", null));
		}
		
		// Walk items once and patch in context for every lang-key attribute we recognize.
		for (var item : source.getItems()) {
			for (var attr : item.getLangAttributes()) {
				final String key = attr.getValue();
				if (key.isEmpty())
					continue;
				final LangEntry existing = entryByKey.get(key);
				// Only attach context to the first item that references this key
				if (existing != null && existing.item == null) {
					entryByKey.put(key, new LangEntry(existing.langKey, existing.value, attr.getName(), item));
				}
			}
		}
		return entryByKey;
	}
	
	// =========================================================================
	//  Data loading
	// =========================================================================
	
	@Override
	public void refresh(Object... input) {
		populateModSelector();
		populateLangSelector();
		// selectors fire actionListeners → refreshAll() is called automatically
	}
	
	private JPanel buildTopBar() {
		JPanel bar = new JPanel(new BorderLayout(12, 0));
		bar.setOpaque(false);
		
		// Left: page title
		bar.add(header("Localization"), BorderLayout.WEST);
		
		// Right: selectors + search
		JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		controls.setOpaque(false);
		
		// ── Mod selector ──────────────────────────────────────────────────
		BaseEditPage.styleCombo(modSelector);
		modSelector.setPreferredSize(new Dimension(200, 28));
		modSelector.setToolTipText("Source mod (Base Game or a user mod)");
		modSelector.addActionListener(e -> refreshAll());
		
		// ── Language selector ─────────────────────────────────────────────
		BaseEditPage.styleCombo(langSelector);
		langSelector.setPreferredSize(new Dimension(130, 28));
		langSelector.setToolTipText("Language to display");
		langSelector.addActionListener(e -> refreshAll());
		
		// ── Attribute-type selector ───────────────────────────────────────
		for (String opt : ATTR_TYPE_OPTIONS)
			attrSelector.addItem(opt);
		BaseEditPage.styleCombo(attrSelector);
		attrSelector.setPreferredSize(new Dimension(170, 28));
		attrSelector.setToolTipText("Filter by attribute hint");
		attrSelector.addActionListener(e -> applyFilters());
		
		// ── Search ────────────────────────────────────────────────────────
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
		
		controls.add(muted("Mod:"));
		controls.add(modSelector);
		controls.add(muted("Lang:"));
		controls.add(langSelector);
		controls.add(muted("Type:"));
		controls.add(attrSelector);
		controls.add(search);
		
		bar.add(controls, BorderLayout.EAST);
		return bar;
	}
	
	// =========================================================================
	//  Filtering
	// =========================================================================
	
	private JSplitPane buildCenter() {
		// ── Left: entry list ──────────────────────────────────────────────
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
		
		// Right-click popup
		JPopupMenu popup = getJPopupMenu();
		entryList.setComponentPopupMenu(popup);
		
		// Double-click copies key
		entryList.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && selectedEntry != null) {
					Util.copyText(selectedEntry.langKey);
					window.snackbar.show("Key copied: ", BarManager.Type.INFO, selectedEntry.langKey);
				}
			}
		});
		
		JScrollPane listScroll = new JScrollPane(entryList);
		listScroll.setBackground(MainWindow.SURFACE);
		listScroll.getViewport().setBackground(new Color(0x181825));
		listScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(0x313244)), "Entries", javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP, new Font("Roboto", Font.BOLD, 12), MainWindow.ACCENT));
		
		// ── Right: detail pane ────────────────────────────────────────────
		detailPane.setContentType("text/html");
		detailPane.setEditable(false);
		detailPane.setBackground(new Color(0x181825));
		detailPane.setForeground(MainWindow.TEXT);
		detailPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
		detailPane.setText(emptyDetailHtml());
		
		JScrollPane detailScroll = new JScrollPane(detailPane);
		detailScroll.setBackground(MainWindow.SURFACE);
		detailScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(0x313244)), "Detail", javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP, new Font("Roboto", Font.BOLD, 12), MainWindow.ACCENT));
		
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, detailScroll);
		split.setResizeWeight(0.55);
		split.setDividerSize(4);
		split.setBackground(MainWindow.BG);
		split.setBorder(BorderFactory.createEmptyBorder());
		return split;
	}
	
	private JPopupMenu getJPopupMenu() {
		JPopupMenu popup = new JPopupMenu();
		JMenuItem copyKey = new JMenuItem("Copy Key");
		copyKey.addActionListener(e -> {
			if (selectedEntry != null) {
				Util.copyText(selectedEntry.langKey);
				window.snackbar.show("Key copied: ", BarManager.Type.INFO, selectedEntry.langKey);
			}
		});
		JMenuItem copyVal = new JMenuItem("Copy Value");
		copyVal.addActionListener(e -> {
			if (selectedEntry != null) {
				Util.copyText(selectedEntry.value);
				window.snackbar.show("Value copied", BarManager.Type.INFO);
			}
		});
		JMenuItem goToItem = new JMenuItem("Go to Item Edit");
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
	
	// =========================================================================
	//  Display helpers
	// =========================================================================
	
	private void populateModSelector() {
		// Suppress action events while rebuilding
		var listeners = modSelector.getActionListeners();
		for (var l : listeners)
			modSelector.removeActionListener(l);
		
		modSelector.removeAllItems();
		modSelector.addItem("🎮  Base Game");
		for (ModData mod : ModService.modCollection) {
			modSelector.addItem("📦  " + mod.id + " | " + mod.name);
		}
		
		for (var l : listeners)
			modSelector.addActionListener(l);
	}
	
	private void populateLangSelector() {
		var listeners = langSelector.getActionListeners();
		for (var l : listeners)
			langSelector.removeActionListener(l);
		
		langSelector.removeAllItems();
		
		// Put the user's configured language first, then the rest
		final Language defLang = window.getRegistry().userConfig.getLanguage();
		
		List<Language> ordered = new ArrayList<>();
		if (defLang != null)
			ordered.add(defLang);
		for (Language lang : Language.values()) {
			if (lang != defLang)
				ordered.add(lang);
		}
		for (Language lang : ordered) {
			langSelector.addItem(lang.getDisplayName());
		}
		
		for (var l : listeners)
			langSelector.addActionListener(l);
		
		// Trigger data load once
		refreshAll();
	}
	
	/**
	 * Full reload: resolve all lang entries for the selected mod + language,
	 * then apply current filters.
	 */
	private void refreshAll() {
		allEntries.clear();
		selectedEntry = null;
		detailPane.setText(emptyDetailHtml());
		
		final ModData source = resolveSelectedMod();
		final Language lang = resolveSelectedLang();
		
		if (source == null || lang == null) {
			applyFilters();
			return;
		}
		
		final var langMap = source.getLang(lang);
		if (langMap.isEmpty()) {
			applyFilters();
			window.snackbar.show("No localization data for ", BarManager.Type.WARNING, lang.getDisplayName());
			return;
		}
		
		// Build a reverse index: langKey → (item, attrName) from items in this source.
		// Done once here so we avoid a separate map allocation and second pass.
		final var entryByKey = getLangEntryMap(langMap, source);
		
		allEntries.addAll(entryByKey.values());
		
		// Sort for readability: by attr name, then lang key
		allEntries.sort(Comparator.comparing((LangEntry le) -> le.attrName, Comparator.nullsLast(String::compareToIgnoreCase)).thenComparing(le -> le.langKey, String.CASE_INSENSITIVE_ORDER));
		
		applyFilters();
		window.snackbar.show("Loaded localization entries : ", BarManager.Type.SUCCESS, allEntries.size());
	}
	
	// =========================================================================
	//  Selector resolution helpers
	// =========================================================================
	
	private void applyFilters() {
		final var rawSearch = search.getText().trim().toLowerCase(Locale.ROOT);
		final boolean noSearch = rawSearch.isEmpty() || rawSearch.equals("search keys or values…");
		final var attrFilter = (String) attrSelector.getSelectedItem();
		final boolean allAttrs = attrFilter == null || attrFilter.equals(ALL_ATTR_TYPES);
		
		// Collect matching indices directly — no parallel list needed
		final int[] indices = new int[allEntries.size()];
		int count = 0;
		for (int i = 0; i < allEntries.size(); i++) {
			final LangEntry entry = allEntries.get(i);
			if ((allAttrs || entry.attrName.startsWith(attrFilter)) && (noSearch || entry.has(rawSearch)))
				indices[count++] = i;
		}
		filteredIndices = Arrays.copyOf(indices, count);
		
		// Rebuild list model without triggering selection events
		entryList.setValueIsAdjusting(true);
		listModel.clear();
		for (int idx : filteredIndices) {
			final var next = allEntries.get(idx);
			listModel.addElement(formatRow(next));
		}
		entryList.setValueIsAdjusting(false);
	}
	
	private ModData resolveSelectedMod() {
		int idx = modSelector.getSelectedIndex();
		if (idx <= 0) {
			// Base Game
			return Singleton.INSTANCE.getGame();
		}
		var mods = ModService.modCollection;
		int modIdx = idx - 1;
		return (modIdx < mods.size()) ? mods.get(modIdx) : null;
	}
	
	// =========================================================================
	//  Shared combo styling (mirrors BaseEditPage.styleCombo)
	// =========================================================================
	
	private Language resolveSelectedLang() {
		Object sel = langSelector.getSelectedItem();
		return sel == null ? null : Language.fromDisplayName(sel.toString());
	}
	
	// =========================================================================
	//  Internal data model
	// =========================================================================
	
	/** One resolved localization entry. */
	record LangEntry(String langKey, String value, String attrName, ModItem item) {
			public boolean has(String inp) {
				return langKey.toLowerCase(Locale.ROOT).contains(inp) || value.toLowerCase(Locale.ROOT).contains(inp);
			}
		}
}