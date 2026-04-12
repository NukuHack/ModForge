package com.nukuhack.modforge.frontend.pages;

import com.nukuhack.modforge.Singleton;
import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.backend.ModData;
import com.nukuhack.modforge.backend.model.ModItem;
import com.nukuhack.modforge.frontend.BarManager;
import com.nukuhack.modforge.frontend.MainWindow;
import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.nukuhack.modforge.frontend.MainWindow.getLocalText;

@Slf4j
@ExtensionMethod({ Util.class })
public class LocalizationPage extends BasePage {
	
	private static final String ALL_ATTR_TYPES = "All Attributes";
	private static final int CHUNK_SIZE = 5_000;
	
	private static final int DEBOUNCE_MS = 250;
	
	private static final List<String> ATTR_TYPE_OPTIONS;
	
	static {
		List<String> sorted = new ArrayList<>(ModItem.LANG_ATTR_HINTS);
		Collections.sort(sorted);
		sorted.add(0, ALL_ATTR_TYPES);
		ATTR_TYPE_OPTIONS = Collections.unmodifiableList(sorted);
	}
	
	/** Full unfiltered list — written only on EDT after background load. */
	private final List<LangEntry> allEntries = new ArrayList<>();
	
	/** Indices into allEntries that pass the current filter. */
	private int[] filteredIndices = new int[0];
	
	private LangEntry selectedEntry = null;
	
	private final ScheduledExecutorService debouncer = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "localization-debounce");
		t.setDaemon(true);
		return t;
	});
	private ScheduledFuture<?> pendingFilter;
	
	/** Version counter — incremented on every new filter run so stale results are dropped. */
	private final AtomicLong filterVersion = new AtomicLong(0);
	
	private final JComboBox<String> attrSelector = new JComboBox<>();
	
	/** Virtual list — model holds only the VISIBLE row strings. */
	private final DefaultListModel<String> listModel = new DefaultListModel<>();
	private final JList<String> entryList = new JList<>(listModel);
	
	private final JEditorPane detailPane = new JEditorPane();
	
	/** Status / progress bar shown while filtering. */
	private final JLabel statusLabel = new JLabel(" ");
	private final JProgressBar progressBar = new JProgressBar();
	
	public LocalizationPage(MainWindow w) {
		super(w);
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
		setLayout(new BorderLayout(0, 12));
		add(buildTopBar(), BorderLayout.NORTH);
		add(buildCenter(), BorderLayout.CENTER);
		add(buildStatusBar(), BorderLayout.SOUTH);
	}
	
	@Override
	public void refresh(Object... input) {
		refreshModSelector();
		refreshLangSelector();
		refreshAll();
	}
	
	private void refreshAll() {
		allEntries.clear();
		selectedEntry = null;
		filteredIndices = new int[0];
		listModel.clear();
		detailPane.setText(emptyDetailHtml());
		setStatus(getLocalText("ui_loading"), true);
		
		var source = getSelectedMod();
		var lang = getSelectedLang().orElseGet(Singleton.getRegistry().userConfig::getLanguage);
		var mod = source.orElseGet(Singleton::getGame);
		
		executor.submit(() -> {
			var langMap = mod.getLang(lang);
			if (langMap.isEmpty()) {
				SwingUtilities.invokeLater(() -> {
					setStatus(" ", false);
					window.snackbar.show("ui_no_localization_data", BarManager.Type.WARNING, lang.getDisplayName());
				});
				return;
			}
			
			var entryMap = buildLangEntryMap(langMap, mod);
			var sorted = new ArrayList<>(entryMap.values());
			sorted.sort(Comparator.comparing((LangEntry le) -> le.attrName, Comparator.nullsLast(String::compareToIgnoreCase)).thenComparing(le -> le.langKey, String.CASE_INSENSITIVE_ORDER));
			
			SwingUtilities.invokeLater(() -> {
				allEntries.addAll(sorted);
				window.snackbar.show("ui_localization_loaded", BarManager.Type.SUCCESS, allEntries.size());
				scheduleFilter();
				
			});
		});
	}
	
	/** Builds the key→LangEntry map off the EDT. */
	private static Map<String, LangEntry> buildLangEntryMap(Map<String, String> langMap, ModData source) {
		
		final Map<String, LangEntry> map = new LinkedHashMap<>(langMap.size());
		for (var e : langMap.entrySet())
			map.put(e.getKey(), new LangEntry(e.getKey(), e.getValue(), "", null));
		
		for (var item : source.getItems()) {
			for (var attr : item.getLangAttributes()) {
				var key = attr.getValue();
				if (key.isEmpty())
					continue;
				var existing = map.get(key);
				if (existing != null && existing.item == null)
					map.put(key, new LangEntry(existing.langKey, existing.value, attr.getName(), item));
			}
		}
		return map;
	}
	
	private void scheduleFilter() {
		if (pendingFilter != null)
			pendingFilter.cancel(false);
		pendingFilter = debouncer.schedule(this::runFilter, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
	}
	
	/** Runs the filter computation on a background thread, then chunks results onto EDT. */
	private void runFilter() {
		final long version = filterVersion.incrementAndGet();
		
		final String rawSearch = search.getText().trim().toLowerCase(Locale.ROOT);
		final String placeholder = getLocalText("ui_search_all").toLowerCase(Locale.ROOT);
		final boolean noSearch = rawSearch.isEmpty() || rawSearch.equals(placeholder);
		final String attrFilter = (String) attrSelector.getSelectedItem();
		final boolean allAttrs = attrFilter == null || attrFilter.equals(ALL_ATTR_TYPES);
		
		final List<LangEntry> snapshot = List.copyOf(allEntries);
		
		SwingUtilities.invokeLater(() -> setStatus(getLocalText("ui_filtering"), true));
		
		executor.submit(() -> {
			int[] indices = new int[snapshot.size()];
			int count = 0;
			
			for (int i = 0; i < snapshot.size(); i++) {
				if (filterVersion.get() != version)
					return;
				
				var entry = snapshot.get(i);
				if ((allAttrs || entry.attrName.startsWith(attrFilter)) && (noSearch || entry.has(rawSearch)))
					indices[count++] = i;
			}
			
			if (filterVersion.get() != version)
				return;
			
			final int[] result = Arrays.copyOf(indices, count);
			final int totalCount = count;
			
			SwingUtilities.invokeLater(() -> {
				if (filterVersion.get() != version)
					return;
				filteredIndices = result;
				entryList.setValueIsAdjusting(true);
				listModel.clear();
				
				int pushed = 0;
				while (pushed < totalCount) {
					int end = Math.min(pushed + CHUNK_SIZE, totalCount);
					for (int i = pushed; i < end; i++)
						listModel.addElement(formatRow(snapshot.get(result[i])));
					pushed = end;
				}
				
				entryList.setValueIsAdjusting(false);
				setStatus(totalCount + " " + getLocalText("ui_entries"), false);
			});
		});
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
		attrSelector.addActionListener(e -> scheduleFilter());
		
		search.setPreferredSize(new Dimension(240, 28));
		search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			public void insertUpdate(DocumentEvent e) {
				scheduleFilter();
			}
			
			public void removeUpdate(DocumentEvent e) {
				scheduleFilter();
			}
			
			public void changedUpdate(DocumentEvent e) {
				scheduleFilter();
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
		detailPane.setComponentPopupMenu(buildDetailPopupMenu());
		detailPane.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && selectedEntry != null) {
					Util.copyText(selectedEntry.langKey);
					window.snackbar.show("ui_copied_key", BarManager.Type.INFO, selectedEntry.langKey);
				}
			}
		});
		
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
	
	private JPanel buildStatusBar() {
		var bar = new JPanel(new BorderLayout(8, 0));
		bar.setOpaque(false);
		bar.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		
		statusLabel.setFont(new Font("Roboto", Font.ITALIC, 11));
		statusLabel.setForeground(MainWindow.MUTED);
		bar.add(statusLabel, BorderLayout.WEST);
		
		progressBar.setPreferredSize(new Dimension(120, 8));
		progressBar.setIndeterminate(true);
		progressBar.setVisible(false);
		progressBar.setBackground(new Color(0x313244));
		progressBar.setForeground(MainWindow.ACCENT);
		progressBar.setBorderPainted(false);
		bar.add(progressBar, BorderLayout.EAST);
		return bar;
	}
	
	private void setStatus(String text, boolean busy) {
		statusLabel.setText(text);
		statusLabel.setForeground(busy ? new Color(0xf9e2af) : MainWindow.MUTED);
		progressBar.setVisible(busy);
	}
	
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
		popup.add(copyKey);
		popup.add(copyVal);
		return popup;
	}
	
	private JPopupMenu buildDetailPopupMenu() {
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
		popup.add(copyKey);
		popup.add(copyVal);
		popup.addSeparator();
		
		var editItem = new JMenuItem(getLocalText("ui_edit_item"));
		editItem.addActionListener(e -> {
			if (selectedEntry != null && selectedEntry.item != null)
				window.navigate(MainWindow.Page.ITEM_EDIT, selectedEntry.item);
		});
		var editLang = new JMenuItem(getLocalText("ui_edit_lang"));
		editLang.addActionListener(e -> {
			if (selectedEntry != null && selectedEntry.item != null)
				window.navigate(MainWindow.Page.LANG_EDIT, selectedEntry.item);
		});
		popup.add(editItem);
		popup.add(editLang);
		return popup;
	}
	
	private static String formatRow(LangEntry le) {
		String key = le.langKey;
		if (key.length() > 48)
			key = key.substring(0, 45) + "…";
		var sb = new StringBuilder();
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
		var html = new StringBuilder();
		html.append("<html><body style='background:#181825;color:#cdd6f4;" + "font-family:sans-serif;padding:14px;margin:0;'>");
		
		html.append("<b style='color:#89b4fa;font-size:13px;'>").append(((le.langKey)).unescapeXml().escHtml()).append("</b>");
		html.append("<hr style='border-color:#313244;margin:8px 0;'/>");
		
		if (! le.attrName.isBlank()) {
			html.append("<div style='margin-bottom:8px;" + "background:#1e1e2e;border-left:3px solid #cba6f7;" + "padding:6px 10px;border-radius:3px;'>");
			html.append("<span style='color:#6c6f85;font-size:9px;" + "text-transform:uppercase;letter-spacing:0.5px;'>").append(getLocalText("ui_attribute")).append("</span><br/>");
			html.append("<span style='color:#cba6f7;font-size:11px;'>").append(le.attrName.unescapeXml().escHtml()).append("</span>");
			html.append("</div>");
		}
		
		html.append("<div style='margin-bottom:10px;" + "background:#1e1e2e;border-left:3px solid #a6e3a1;" + "padding:8px 10px;border-radius:3px;'>");
		html.append("<span style='color:#6c6f85;font-size:9px;" + "text-transform:uppercase;letter-spacing:0.5px;'>").append(getLocalText("ui_value")).append("</span><br/>");
		html.append("<span style='color:#a6e3a1;font-size:13px;'>").append(le.value.unescapeXml().escHtml()).append("</span>");
		html.append("</div>");
		
		if (le.item != null) {
			html.append("<hr style='border-color:#313244;margin:8px 0;'/>");
			html.append("<span style='color:#6c6f85;font-size:9px;" + "text-transform:uppercase;letter-spacing:0.5px;'>").append(getLocalText("ui_item_context")).append("</span><br/><br/>");
			
			String itemHtml = htmlForItem(le.item);
			int bodyStart = itemHtml.indexOf("<body");
			int bodyTagEnd = itemHtml.indexOf('>', bodyStart) + 1;
			int bodyClose = itemHtml.lastIndexOf("</body>");
			if (bodyStart >= 0 && bodyClose >= 0)
				html.append(itemHtml, bodyTagEnd, bodyClose);
		}
		
		html.append("</body></html>");
		return html.toString();
	}
	
	record LangEntry(String langKey, String value, String attrName, ModItem item) {
		public boolean has(String inp) {
			return langKey.toLowerCase(Locale.ROOT).contains(inp) || value.toLowerCase(Locale.ROOT).contains(inp);
		}
	}
}